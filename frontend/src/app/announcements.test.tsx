/**
 * 게임 보상 알림 — 체크인 뒤 서버가 늦게 반영한 보호권 사용·연속 탐험 마일스톤·시·도 정복·이번 주 미스터리 보너스를 알리고,
 * 정복한 시·도는 지도에서 잠깐 반짝인다. (9단계) 계절 한정 회차 완성·가고 싶던 곳 다녀옴·재방문 2회차 색·계절 배경도 알린다.
 * 처음 읽을 때나 예시 데이터처럼 알리지 않기로 한 변경은 알리지 않는다.
 */
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { WishlistResponse } from '../api/types/exploration';
import type { MysteryWeekResponse, ProgressResponse, SeasonRoundResponse, SeasonsResponse } from '../api/types/progression';
import type { InventoryResponse, OwnedItemResponse } from '../api/types/wardrobe';
import { seasonKeys } from '../shared/queries/seasons';
import { wardrobeKeys } from '../shared/queries/wardrobe';
import { wishlistKeys } from '../shared/queries/wishlist';
import { catalogKeys } from '../shared/queries/catalog';
import { mysteryKeys } from '../shared/queries/mystery';
import { progressKeys } from '../shared/queries/progress';
import { useSyncStore } from '../store/syncStore';
import { useToastStore } from '../store/toastStore';
import { useUiStore } from '../store/uiStore';
import { CATALOG } from '../test/fixtures';
import { serverState } from '../test/serverState';
import { useAnnouncements } from './useAnnouncements';

vi.mock('../api/progression', () => ({ progressionApi: { progress: vi.fn(() => new Promise(() => undefined)), mysteryThisWeek: vi.fn(() => new Promise(() => undefined)), seasons: vi.fn(() => new Promise(() => undefined)) } }));
vi.mock('../api/exploration', () => ({ explorationApi: { wishlist: vi.fn(() => new Promise(() => undefined)) } }));
vi.mock('../api/wardrobe', () => ({ wardrobeApi: { inventory: vi.fn(() => new Promise(() => undefined)), scene: vi.fn(() => new Promise(() => undefined)) } }));

const THIS_MONTH = { month: '2026-12', questsRewarded: 0, questsRequired: 4, reward: 1, earned: false, granted: 0 };
const progress = (overrides: Partial<ProgressResponse> = {}): ProgressResponse => ({
  explorerId: 'me', xp: 0, level: 1, levelTitle: '초보 탐험가', currentLevelXp: 0, nextLevelXp: 40, title: null, selectedTitleId: null,
  streak: { months: 3, lastMonth: '2026-12', activeThisMonth: true, freezesNeeded: 0, frozenMonths: [] }, badgeCount: 0, badges: [], titles: [], recentXp: [],
  streakFreeze: { held: 0, max: 2, lastUsed: null, thisMonth: THIS_MONTH }, nextMilestone: null,
  milestones: [{ months: 3, xp: 50, freezes: 1, titleId: 'streak-3', titleName: '꾸준한 탐험가', reached: false, reachedAt: null, remainingMonths: 1 }],
  provinces: [{ code: 'KR-11', name: '서울', visited: 1, total: 2, percent: 50, complete: false, conquered: false, conqueredAt: null }],
  mysteryFoundCount: 0, revisitStampCount: 0, wishFulfilledCount: 0, ...overrides,
});
const week = (received: boolean): MysteryWeekResponse => ({
  weekStart: '2026-09-28', startsAt: '', endsAt: '', remainingSeconds: 100,
  region: { code: 'KR-31370', name: '가평군', provinceCode: 'KR-31', provinceName: '경기', rarity: 'RARE' }, bonusXp: 50, received, receivedAt: null, foundCount: received ? 1 : 0, revealed: received,
});

const round = (overrides: Partial<SeasonRoundResponse> = {}): SeasonRoundResponse => ({
  roundId: 'autumn-2026', seasonId: 'autumn', name: '2026 단풍 명소', emoji: '🍁', year: 2026, startsAt: '', endsAt: '', remainingSeconds: 100, open: true,
  have: 9, total: 10, completed: false, completedAt: null, rewarded: false, xp: 150, titleId: 'season-autumn', titleName: '단풍 사냥꾼', backgroundItemId: 'season:autumn-2026', regions: [], provenance: 'ai-estimate', source: null, ...overrides,
});
const seasons = (current: SeasonRoundResponse): SeasonsResponse => ({ mapId: 'personal', now: '', current: [current], next: null, history: [] });
const wishlist = (status: 'WANTED' | 'VISITED'): WishlistResponse => ({
  max: 30, pendingCount: status === 'WANTED' ? 1 : 0, fulfilledCount: status === 'VISITED' ? 1 : 0, xpPerWish: 20,
  items: [{ regionCode: 'KR-31370', regionName: '가평군', provinceCode: 'KR-31', status, pinnedAt: '', fulfilledAt: status === 'VISITED' ? '2026-10-04T00:00:00Z' : null }],
});
const owned = (itemId: string, overrides: Partial<OwnedItemResponse> = {}): OwnedItemResponse => ({
  itemId, name: '가평 잣 다람쥐', emoji: '🐿️', slot: 'PET', tier: 'RARE', theme: null, look: null, regionCode: 'KR-31370', variant: 1, variantLook: null,
  source: 'REGION', acquiredAt: '', favorite: false, equipped: false, ...overrides,
});
const inventory = (...items: OwnedItemResponse[]): InventoryResponse => ({ count: items.length, items });

function screenAfterCheckIn(announce = true) {
  const state = serverState([
    [catalogKeys.index(), CATALOG], [progressKeys.progress(), progress()], [mysteryKeys.thisWeek(), week(false)],
    [seasonKeys.current(), seasons(round())], [wishlistKeys.list(), wishlist('WANTED')], [wardrobeKeys.inventory(), inventory(owned('region:KR-31370'))],
  ]);
  renderHook(() => useAnnouncements(), { wrapper: state.wrapper });
  useSyncStore.setState({ announce });
  // 서버 값 도착 — 캐시 알림은 다음 틱에 화면으로 전해진다
  const arrive = (key: readonly unknown[], value: unknown) => act(async () => {
    state.queryClient.setQueryData(key, value);
    await new Promise(resolve => setTimeout(resolve, 0));
  });
  const toasts = () => useToastStore.getState().toasts.map(toast => toast.title);
  return { arrive, toasts };
}

afterEach(() => {
  cleanup();
  useSyncStore.setState({ settleUntil: 0, announce: false });
  useToastStore.setState({ toasts: [] });
  useUiStore.setState({ mapCommand: null });
});

describe('게임 보상 알림', () => {
  it('보호권으로 스트릭을 지키면 몇 개를 썼는지 알린다', async () => {
    const { arrive, toasts } = screenAfterCheckIn();
    await arrive(progressKeys.progress(), progress({ streakFreeze: { held: 0, max: 2, lastUsed: { month: '2027-01', count: 1, at: '2027-01-05T00:00:00Z' }, thisMonth: THIS_MONTH } }));
    expect(toasts()).toContain('보호권 1개로 스트릭을 지켰어요');
  });

  it('연속 탐험 마일스톤을 달성하면 몇 개월인지 알린다', async () => {
    const { arrive, toasts } = screenAfterCheckIn();
    await arrive(progressKeys.progress(), progress({ milestones: [{ ...progress().milestones[0], reached: true, remainingMonths: 0 }] }));
    expect(toasts()).toContain('연속 탐험 3개월 달성');
  });

  it('시·도를 정복하면 정복을 알리고 지도에서 그 시·도 테두리를 반짝인다', async () => {
    const { arrive, toasts } = screenAfterCheckIn();
    await arrive(progressKeys.progress(), progress({ provinces: [{ ...progress().provinces[0], visited: 2, percent: 100, complete: true, conquered: true }] }));
    expect(toasts()).toContain('서울 정복!');
    expect(useToastStore.getState().toasts.find(toast => toast.title === '서울 정복!')?.sub).toContain('+300 XP');
    expect(useUiStore.getState().mapCommand).toMatchObject({ kind: 'flash-provinces', provinces: ['서울'] });
  });

  it('이번 주 미스터리 지역을 칠해 보너스를 받으면 알린다', async () => {
    const { arrive, toasts } = screenAfterCheckIn();
    await arrive(mysteryKeys.thisWeek(), week(true));
    expect(toasts()).toContain('이번 주 미스터리 지역을 찾았어요');
  });

  it('계절 한정 회차를 완성해 보상을 받으면 회차 이름과 칭호·XP 를 알린다', async () => {
    const { arrive, toasts } = screenAfterCheckIn();
    await arrive(seasonKeys.current(), seasons(round({ have: 10, completed: true, rewarded: true })));
    expect(toasts()).toContain('2026 단풍 명소 완성!');
    expect(useToastStore.getState().toasts.find(toast => toast.title === '2026 단풍 명소 완성!')?.sub).toBe('칭호 「단풍 사냥꾼」 · +150 XP · 계절 배경');
  });

  it('가고 싶은 곳에 꽂아 둔 지역을 칠해 다녀옴이 되면 축하한다', async () => {
    const { arrive, toasts } = screenAfterCheckIn();
    await arrive(wishlistKeys.list(), wishlist('VISITED'));
    expect(toasts()).toContain('가고 싶던 가평군에 다녀왔어요');
  });

  it('회차 배경이 가방에 들어오면 계절 배경 해금을, 특산물이 2회차 색으로 바뀌면 색 변형을 알린다', async () => {
    const { arrive, toasts } = screenAfterCheckIn();
    await arrive(wardrobeKeys.inventory(), inventory(
      owned('region:KR-31370', { variant: 2 }),
      owned('season:autumn-2026', { name: '2026 단풍 명소 배경', slot: 'BG', source: 'SET_REWARD', regionCode: null }),
    ));
    expect(toasts()).toEqual(expect.arrayContaining(['계절 배경 해금 · 2026 단풍 명소 배경', '2회차 색 · 가평 잣 다람쥐']));
  });

  it('알리지 않기로 한 변경(예시 채우기 등)이면 정복해도 알리지 않는다', async () => {
    const { arrive, toasts } = screenAfterCheckIn(false);
    await arrive(progressKeys.progress(), progress({ provinces: [{ ...progress().provinces[0], conquered: true }] }));
    expect(toasts()).toEqual([]);
    expect(useUiStore.getState().mapCommand).toBeNull();
  });
});
