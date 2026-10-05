/**
 * 지도 탭 — 내 영토를 보고, 지역을 눌러 칠하고(체크인), 고치고, 지운다. 정복률·시·도별 값은 서버 값이고 화면은 고르기·정렬·문구만 한다.
 * 이야기 순서: 내 영토 보기 → 이번 주 미스터리 지역 → 지역 누르기 → 체크인 모달 → 체크인한 뒤 → 기록 수정·취소 → 예시 채우기·전부 지우기 → 공유 지도 이의
 * → (9단계) 계절 한정 배지 → 재방문 도장 → 가고 싶은 곳.
 */
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { explorationApi } from '../../api/exploration';
import type { MapDetailResponse } from '../../api/types/exploration';
import type { MysteryWeekResponse, SetResponse } from '../../api/types/progression';
import { ApiError } from '../../api/client';
import type { StampStatusResponse, WishlistResponse } from '../../api/types/exploration';
import type { SeasonRoundResponse, SeasonsResponse } from '../../api/types/progression';
import { mysteryKeys } from '../../shared/queries/mystery';
import { seasonKeys } from '../../shared/queries/seasons';
import { wishlistKeys } from '../../shared/queries/wishlist';
import { errorTitle } from '../../store/toastStore';
import { RevisitStamp } from './components/RevisitStamp';
import { SeasonBadge } from './components/SeasonBadge';
import { WishlistCard } from './components/WishlistCard';
import { WishToggle } from './components/WishToggle';
import { revisitRefusalText, revisitView, stampedToast, stampErrorText } from './model/revisit';
import { pendingWishCodes, pinErrorText, wishSections, wishToggle } from './model/wishlist';
import { MysteryCard } from './components/MysteryCard';
import { mysteryCard, remainingText } from './model/mystery';
import { myVisits, type MyVisit } from '../../shared/lib/territory/visits';
import { recapKeys } from '../../shared/queries/recap';
import { mapKeys } from '../../shared/queries/territory';
import { SETTLED_ROOT, settleInterval, SETTLE_INTERVAL_MS, useSyncStore } from '../../store/syncStore';
import { useToastStore } from '../../store/toastStore';
import { useUiStore } from '../../store/uiStore';
import { CATALOG, territory, visit } from '../../test/fixtures';
import { serverState } from '../../test/serverState';
import { claimMaps, latestVisitCode, localIsoDate, logEntries, provinceRows, regionTip, setChips, showsProvinceFirstHint } from './model/territory';
import { revisitKeys, useCancelVisit, useCheckIn, useClearVisits, useDispute, useEditVisit, useMapActions, usePin, useSeed, useStamp } from './queries';

vi.mock('../../api/exploration', () => ({
  explorationApi: {
    checkIn: vi.fn(async () => ({ mapId: 'personal', nth: 1, xp: { lines: [], total: 35, basis: 'MAP_MAX', note: '' }, items: [] })),
    cancelVisit: vi.fn(async () => null),
    editVisit: vi.fn(async () => null),
    dispute: vi.fn(async () => null),
    revisitStatus: vi.fn(() => new Promise(() => undefined)),
    stamp: vi.fn(async () => ({ regionCode: 'KR-31370', regionName: '가평군', year: 2027, firstYear: 2026, stampedAt: '', xp: 10, stampCount: 1 })),
    wishlist: vi.fn(() => new Promise(() => undefined)),
    pin: vi.fn(),
    unpin: vi.fn(async () => null),
  },
}));
vi.mock('../../api/dev', () => ({ devApi: { seed: vi.fn(async () => ({ seeded: 45 })), clearVisits: vi.fn(async () => null) } }));
vi.mock('../../shared/queries/collection', () => ({ useCollection: () => ({ data: undefined }) }));
vi.mock('../../shared/queries/social', () => ({ usePercentile: () => ({ data: undefined }) }));

const detail = (members: MapDetailResponse['members']): MapDetailResponse => ({
  mapId: 'shared', name: '원정대', kind: 'SHARED', countryCode: 'KR', ownerId: 'me', inviteCode: 'ABCDEFGH',
  settings: { photoRequired: false, dailyCheckInCap: 5, visibility: 'PRIVATE' }, members, claims: [], disputed: [], departing: 0, rules: [], rejoined: false,
});
const member = (explorerId: string, me: boolean, color: string) => ({ explorerId, role: me ? 'OWNER' as const : 'MEMBER' as const, joinedAt: '', color, me, regionCount: 1, claimCount: 1 });

const SEOUL_PAIR: SetResponse = {
  id: 'seoul', name: '서울 둘', desc: '', title: '서울러', have: 1, total: 2, completed: false, completedAt: null, rewardXp: 100,
  regions: [{ code: 'KR-11010', name: '종로구', collected: true }, { code: 'KR-11020', name: '중구', collected: false }],
};

/** 화면이 지켜보는 서버 값(영토·공유 지도 상세·진행·친구·리캡)이 이미 읽혀 있는 상태 */
const screenWithServerValues = () => serverState([
  [mapKeys.territory(null), { mapId: 'personal' }],
  [mapKeys.detail('shared'), { mapId: 'shared' }],
  [[SETTLED_ROOT, 'progress'], { xp: 0 }],
  [['social', 'friends'], { people: [] }],
  [recapKeys.recap(2026, null), { newRegions: 0 }],
]);

const CHECK_IN = { code: '11010', visitDate: '2026-10-03', memo: '', photoUrl: '', mapId: null };
const visitsOf = (...codes: string[]): Map<string, MyVisit> => new Map(codes.map(code => [code, { code, date: '', memo: '', at: 0 }]));

afterEach(() => {
  cleanup();
  useUiStore.setState({ mysteryRevealedWeek: null, focusRegions: [] });
  vi.clearAllMocks();
  useSyncStore.setState({ settleUntil: 0, territoryUntil: 0, announce: false });
  useUiStore.setState({ sampleMode: false, mode: 'paint', selected: null, checkinCode: null });
  useToastStore.setState({ toasts: [] });
});

describe('내 영토 보기', () => {
  describe('캐릭터', () => {
    it('가장 최근에 처리한 체크인 지역에 선다', () => {
      const visits = myVisits(territory({ visits: [visit('11010', { visitedAt: '2026-10-01T03:00:00Z' }), visit('37430', { visitedAt: '2026-10-02T03:00:00Z' }), visit('11020', { visitedAt: '2026-09-01T03:00:00Z' })] }), null);
      expect(latestVisitCode(visits ?? new Map())).toBe('37430');
    });

    it('칠한 곳이 없으면 설 곳도 없다', () => {
      expect(latestVisitCode(new Map())).toBeNull();
    });
  });

  describe('시·도별 정복률', () => {
    it('서버가 센 값을 정복률 높은 순으로, 같으면 방문 많은 순으로 보여 준다', () => {
      const rows = provinceRows(territory({ provinces: [
        { code: 'KR-11', name: '서울', visited: 1, total: 2, percent: 50, conquered: false },
        { code: 'KR-31', name: '경기', visited: 1, total: 1, percent: 100, conquered: true },
        { code: 'KR-37', name: '경북', visited: 0, total: 1, percent: 0, conquered: false },
      ] }), CATALOG);
      expect(rows.map(row => row.name)).toEqual(['경기', '서울', '경북']);
    });

    it('서버 값이 오기 전에는 카탈로그 시·도 순서로 모두 0곳을 보여 준다', () => {
      expect(provinceRows(null, CATALOG).map(row => `${row.name}${row.visited}/${row.total}`)).toEqual(['서울0/2', '경기0/1', '경북0/1']);
    });

    it('정복한 시·도에는 왕관을 씌우고, 지금 지도에서 덜 칠했어도 정복 기록이 있으면 왕관은 남는다', () => {
      const rows = provinceRows(territory({ provinces: [
        { code: 'KR-11', name: '서울', visited: 1, total: 2, percent: 50, conquered: false },
        { code: 'KR-31', name: '경기', visited: 1, total: 1, percent: 100, conquered: true },
      ] }), CATALOG, new Set(['KR-11']));
      expect(rows.map(row => `${row.name}${row.crowned ? '👑' : ''}`)).toEqual(['경기', '서울👑']);
    });

    it('서버 값이 오기 전에도 정복 기록이 있는 시·도에는 왕관을 씌운다', () => {
      expect(provinceRows(null, CATALOG, new Set(['KR-37'])).filter(row => row.crowned).map(row => row.name)).toEqual(['경북']);
    });
  });

  describe('탐험 일지', () => {
    it('서버가 준 순서대로 지역 이름·메모·이의 표시를 보여 주고, 모르는 지역은 뺀다', () => {
      const entries = logEntries(territory({ visits: [visit('31370', { memo: '잣', disputed: true }), visit('99999'), visit('11010')] }), CATALOG);
      expect(entries.map(entry => entry.label)).toEqual(['경기 가평군', '서울 종로구']);
      expect(entries[0]).toMatchObject({ memo: '잣', disputed: true });
    });

    it('최근 40곳까지만 보여 주고, 영토를 아직 못 읽었으면 비어 있다', () => {
      const many = territory({ visits: Array.from({ length: 45 }, () => visit('11010')) });
      expect(logEntries(many, CATALOG)).toHaveLength(40);
      expect(logEntries(null, CATALOG)).toEqual([]);
    });
  });

  describe('지역 툴팁', () => {
    it('안 간 곳은 미탐험, 전설 지역은 전설이라고 알려 준다', () => {
      const ulleung = CATALOG.byCode.get('37430');
      expect(ulleung && regionTip(ulleung, false, [SEOUL_PAIR])).toBe('경북 울릉군 · 미탐험 · 전설');
    });

    it('내 영토이고 도감 세트에 든 곳이면 세트 이름까지 알려 준다', () => {
      const jongno = CATALOG.byCode.get('11010');
      expect(jongno && regionTip(jongno, true, [SEOUL_PAIR])).toBe('서울 종로구 · 내 영토 · 도감: 서울 둘');
    });
  });

  describe('공유 지도의 선점 색', () => {
    it('먼저 칠한 멤버의 색으로 그 지역을 칠한다', () => {
      const shared = territory({ mapKind: 'SHARED', claims: [{ regionCode: 'KR-11010', explorerId: 'friend' }] });
      const { claimer, color } = claimMaps(shared, detail([member('me', true, '#2fc3ad'), member('friend', false, '#e8743b')]));
      expect(claimer.get('11010')).toBe('friend');
      expect(color.get('11010')).toBe('#e8743b');
    });

    it('개인 지도이거나 멤버 정보가 아직 없으면 선점 색을 칠하지 않는다', () => {
      const shared = territory({ mapKind: 'SHARED', claims: [{ regionCode: 'KR-11010', explorerId: 'friend' }] });
      expect(claimMaps(territory(), null).color.size).toBe(0);
      expect(claimMaps(shared, null).color.size).toBe(0);
    });

    it('이미 떠난 멤버의 선점은 색 없이 둔다', () => {
      const shared = territory({ mapKind: 'SHARED', claims: [{ regionCode: 'KR-11010', explorerId: 'gone' }] });
      expect(claimMaps(shared, detail([member('me', true, '#2fc3ad')])).claimer.size).toBe(0);
    });
  });
});

describe('이번 주 미스터리 지역', () => {
  const week = (overrides: Partial<MysteryWeekResponse> = {}): MysteryWeekResponse => ({
    weekStart: '2026-09-28', startsAt: '2026-09-27T15:00:00Z', endsAt: '2026-10-04T15:00:00Z', remainingSeconds: 2 * 86400 + 3 * 3600 + 120,
    region: { code: 'KR-31370', name: '가평군', provinceCode: 'KR-31', provinceName: '경기', rarity: 'RARE' },
    bonusXp: 50, received: false, receivedAt: null, foundCount: 0, revealed: false, ...overrides,
  });

  describe('남은 기간', () => {
    it('하루 넘게 남으면 며칠 몇 시간 남았는지 보여 준다', () => {
      expect(remainingText(2 * 86400 + 3 * 3600 + 59)).toBe('2일 3시간 남음');
    });

    it('하루가 안 남으면 몇 시간 몇 분, 한 시간이 안 남으면 몇 분 남았는지 보여 준다', () => {
      expect(remainingText(5 * 3600 + 7 * 60)).toBe('5시간 7분 남음');
      expect(remainingText(42 * 60 + 10)).toBe('42분 남음');
    });

    it('1분도 안 남으면 곧 다음 주 지역으로 바뀐다고 알려 준다', () => {
      expect(remainingText(30)).toBe('곧 다음 주 지역으로 바뀌어요');
      expect(remainingText(-5)).toBe('곧 다음 주 지역으로 바뀌어요');
    });
  });

  describe('미스터리 카드', () => {
    it('누르기 전에는 지역 이름 대신 희귀도만 알려 주고, 남은 기간과 보너스 XP 를 보여 준다', () => {
      expect(mysteryCard(week(), null)).toMatchObject({ title: '어딘가의 희귀 지역', remaining: '2일 3시간 남음', bonus: '+50 XP', received: false });
      expect(mysteryCard(week({ region: { ...week().region, rarity: 'LEGEND' } }), null).title).toBe('어딘가의 전설 지역');
    });

    it('카드나 마커를 눌러 지도에서 찾으면 시·도와 지역 이름을 보여 준다', () => {
      expect(mysteryCard(week(), '2026-09-28').title).toBe('경기 가평군');
    });

    it('탭을 연 채 주가 넘어가면 지난주에 공개한 이름은 새 주 지역에 이어지지 않고 다시 숨는다', () => {
      const nextWeek = week({ weekStart: '2026-10-05', region: { code: 'KR-37430', name: '울릉군', provinceCode: 'KR-37', provinceName: '경북', rarity: 'LEGEND' } });
      expect(mysteryCard(nextWeek, '2026-09-28')).toMatchObject({ title: '어딘가의 전설 지역', revealed: false });
    });

    it('이번 주 보너스를 받아 서버가 공개해도 된다고 하면 누르지 않아도 지역 이름을 보여 준다', () => {
      expect(mysteryCard(week({ received: true, revealed: true }), null)).toMatchObject({ title: '경기 가평군', revealed: true });
    });

    it('이번 주 보너스를 받았으면 받았다고 표시하고 지금까지 찾은 주 수를 알려 준다', () => {
      expect(mysteryCard(week({ received: true, revealed: true, foundCount: 3 }), '2026-09-28')).toMatchObject({ received: true, hint: '이번 주 보너스를 받았어요 · 지금까지 3주 찾음' });
    });

    it('카드를 누르면 지역 이름을 공개하고 지도에서 그 지역으로 확대해 강조한다', () => {
      const { wrapper } = serverState([[mysteryKeys.thisWeek(), week()]]);
      render(<MysteryCard />, { wrapper });
      expect(screen.getByText('어딘가의 희귀 지역')).toBeTruthy();
      fireEvent.click(screen.getByRole('button'));
      expect(screen.getByText('경기 가평군')).toBeTruthy();
      expect(useUiStore.getState()).toMatchObject({ mysteryRevealedWeek: '2026-09-28', highlight: '31370', selected: '31370' });
      expect(useUiStore.getState().mapCommand).toMatchObject({ kind: 'zoom', codes: ['31370'] });
    });
  });
});

describe('지역 누르기', () => {
  const actionsOn = (visits: Map<string, MyVisit>) => {
    const { wrapper } = screenWithServerValues();
    return renderHook(() => useMapActions(CATALOG, visits, null), { wrapper }).result;
  };

  it('칠하기 모드에서 안 간 곳을 누르면 체크인 모달을 띄운다', () => {
    actionsOn(visitsOf()).current.clickRegion('31370');
    expect(useUiStore.getState()).toMatchObject({ selected: '31370', checkinCode: '31370' });
  });

  it('칠하기 모드에서 이미 칠한 곳을 누르면 영토에서 지우고 알려 준다', async () => {
    actionsOn(visitsOf('11010')).current.clickRegion('11010');
    await waitFor(() => expect(useToastStore.getState().toasts.map(toast => toast.title)).toContain('영토에서 제거'));
    expect(vi.mocked(explorationApi.cancelVisit)).toHaveBeenCalledWith('11010', null);
    expect(useUiStore.getState().checkinCode).toBeNull();
  });

  it('기록 모드에서는 고르기만 하고 칠하지도 지우지도 않는다', () => {
    useUiStore.setState({ mode: 'detail' });
    actionsOn(visitsOf('11010')).current.clickRegion('11010');
    expect(useUiStore.getState()).toMatchObject({ selected: '11010', checkinCode: null });
    expect(vi.mocked(explorationApi.cancelVisit)).not.toHaveBeenCalled();
  });
});

describe('체크인 모달', () => {
  it('방문 날짜는 내 기기 달력의 날짜로 적어, 자정 직후나 밤늦게 열어도 그날이다', () => {
    expect(localIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
    expect(localIsoDate(new Date(2026, 0, 5, 0, 30))).toBe('2026-01-05');
    expect(localIsoDate(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05');
  });

  describe('도감 세트 칩', () => {
    it('이 지역이 든 세트마다 내 영토 기준으로 모은 수를 보여 준다', () => {
      expect(setChips([SEOUL_PAIR], '11020', new Set(['11010']))).toEqual([{ id: 'seoul', name: '서울 둘', have: 1, total: 2, done: false }]);
    });

    it('지금 칠하려는 곳도 세어 이번 체크인으로 세트가 완성되는지 미리 보여 준다', () => {
      expect(setChips([SEOUL_PAIR], '11020', new Set(['11010']), '11020')[0]).toMatchObject({ have: 2, done: true });
    });

    it('어느 세트에도 들지 않은 지역이면 칩이 없다', () => {
      expect(setChips([SEOUL_PAIR], '31370', new Set(['11010']))).toEqual([]);
    });
  });

  describe('시·도 첫 방문 표시', () => {
    it('지금 지도에서 그 시·도에 칠한 곳이 하나도 없으면 보인다', () => {
      expect(showsProvinceFirstHint(CATALOG, '11020', [])).toBe(true);
      expect(showsProvinceFirstHint(CATALOG, '11020', ['37430'])).toBe(true);
    });

    it('같은 시·도에 이미 칠한 곳이 있으면 보이지 않는다', () => {
      expect(showsProvinceFirstHint(CATALOG, '11020', ['11010'])).toBe(false);
    });

    it('모르는 지역이면 보이지 않는다', () => {
      expect(showsProvinceFirstHint(CATALOG, '99999', [])).toBe(false);
    });
  });
});

describe('체크인한 뒤', () => {
  it('지도와 공유 지도 멤버 정보를 바로 다시 읽는다', async () => {
    const { wrapper, stale } = screenWithServerValues();
    const { result } = renderHook(() => useCheckIn(), { wrapper });
    await act(() => result.current.mutateAsync(CHECK_IN));
    expect(stale(mapKeys.territory(null))).toBe(true);
    expect(stale(mapKeys.detail('shared'))).toBe(true);
  });

  it('서버가 XP·도감·가방을 늦게 반영하므로 잠깐 동안 짧은 주기로 다시 읽고, 새 뱃지·레벨 업을 알린다', async () => {
    const { wrapper, stale } = screenWithServerValues();
    const { result } = renderHook(() => useCheckIn(), { wrapper });
    await act(() => result.current.mutateAsync(CHECK_IN));
    expect(stale([SETTLED_ROOT, 'progress'])).toBe(true);
    expect(settleInterval()).toBe(SETTLE_INTERVAL_MS);
    expect(useSyncStore.getState().announce).toBe(true);
  });

  it('잠깐이 지나면 다시 읽기를 멈춘다', () => {
    useSyncStore.setState({ settleUntil: Date.now() - 1 });
    expect(settleInterval()).toBe(false);
  });

  it('연간 리캡도 새로 읽는다', async () => {
    const { wrapper, stale } = screenWithServerValues();
    const { result } = renderHook(() => useCheckIn(), { wrapper });
    await act(() => result.current.mutateAsync(CHECK_IN));
    expect(stale(recapKeys.recap(2026, null))).toBe(true);
  });

  it('친구 목록처럼 체크인과 상관없는 값은 다시 읽지 않는다', async () => {
    const { wrapper, stale } = screenWithServerValues();
    const { result } = renderHook(() => useCheckIn(), { wrapper });
    await act(() => result.current.mutateAsync(CHECK_IN));
    expect(stale(['social', 'friends'])).toBe(false);
  });

  it('예시 데이터를 보던 중이었으면 예시 안내를 끈다', async () => {
    useUiStore.setState({ sampleMode: true });
    const { wrapper } = screenWithServerValues();
    const { result } = renderHook(() => useCheckIn(), { wrapper });
    await act(() => result.current.mutateAsync(CHECK_IN));
    expect(useUiStore.getState().sampleMode).toBe(false);
  });
});

describe('기록 수정과 취소', () => {
  it('기록을 고치면 영토를 다시 읽고, 날짜가 바뀌어 달이 달라질 수 있으니 리캡도 새로 읽는다', async () => {
    const { wrapper, stale } = screenWithServerValues();
    const { result } = renderHook(() => useEditVisit(), { wrapper });
    await act(() => result.current.mutateAsync({ code: '11010', visitDate: '2026-03-01', memo: '', mapId: null }));
    expect(stale(mapKeys.territory(null))).toBe(true);
    expect(stale(recapKeys.recap(2026, null))).toBe(true);
  });

  it('칠한 곳을 지우면 체크인과 똑같이 영토·진행·리캡을 다시 읽는다', async () => {
    const { wrapper, stale } = screenWithServerValues();
    const { result } = renderHook(() => useCancelVisit(), { wrapper });
    await act(() => result.current.mutateAsync({ code: '11010', mapId: null }));
    expect(stale(mapKeys.territory(null))).toBe(true);
    expect(stale([SETTLED_ROOT, 'progress'])).toBe(true);
    expect(stale(recapKeys.recap(2026, null))).toBe(true);
  });
});

describe('예시 채우기와 전부 지우기', () => {
  it('예시를 채우면 알림 없이 반영하고 예시 안내를 켠다', async () => {
    const { wrapper } = screenWithServerValues();
    const { result } = renderHook(() => useSeed(), { wrapper });
    await act(() => result.current.mutateAsync());
    await waitFor(() => expect(useUiStore.getState().sampleMode).toBe(true));
    expect(useSyncStore.getState().announce).toBe(false);
  });

  it('전부 지우면 알림 없이 반영하고 예시 안내를 끈다', async () => {
    useUiStore.setState({ sampleMode: true });
    const { wrapper, stale } = screenWithServerValues();
    const { result } = renderHook(() => useClearVisits(), { wrapper });
    await act(() => result.current.mutateAsync());
    expect(stale(mapKeys.territory(null))).toBe(true);
    expect(useUiStore.getState().sampleMode).toBe(false);
    expect(useSyncStore.getState().announce).toBe(false);
  });
});

describe('공유 지도 이의', () => {
  it('지도장이 이의를 걸거나 풀면 영토를 다시 읽어 이의 표시를 바꾼다', async () => {
    const { wrapper, stale } = screenWithServerValues();
    const { result } = renderHook(() => useDispute(), { wrapper });
    await act(() => result.current.mutateAsync({ mapId: 'shared', code: '11010', memberId: 'friend', disputed: true }));
    expect(stale(mapKeys.territory(null))).toBe(true);
    expect(stale([SETTLED_ROOT, 'progress'])).toBe(false);
  });
});

describe('계절 한정 배지', () => {
  const round: SeasonRoundResponse = {
    roundId: 'autumn-2026', seasonId: 'autumn', name: '2026 단풍 명소', emoji: '🍁', year: 2026, startsAt: '2026-09-30T15:00:00Z', endsAt: '2026-11-30T15:00:00Z',
    remainingSeconds: 58 * 86400, open: true, have: 1, total: 2, completed: false, completedAt: null, rewarded: false, xp: 150, titleId: 'season-autumn', titleName: '단풍 사냥꾼',
    backgroundItemId: 'season:autumn-2026',
    regions: [{ code: 'KR-31370', name: '가평군', provinceCode: 'KR-31', collected: true, provenance: 'ai-estimate', evidence: [] }, { code: 'KR-11010', name: '종로구', provinceCode: 'KR-11', collected: false, provenance: 'ai-estimate', evidence: [] }],
    provenance: 'ai-estimate', source: null,
  };
  const seasons = (current: SeasonRoundResponse[]): SeasonsResponse => ({ mapId: 'personal', now: '', current, next: null, history: [] });

  it('계절 기간이면 지도 위에 회차 배지를 달고, 누르면 회차 지역을 모두 강조한다', () => {
    const { wrapper } = serverState([[seasonKeys.current(), seasons([round])]]);
    render(<SeasonBadge />, { wrapper });
    const badge = screen.getByRole('button');
    expect(badge.textContent).toBe('🍁 2026 단풍 명소 1/2 · 58일 남음');
    fireEvent.click(badge);
    expect(useUiStore.getState()).toMatchObject({ tab: 'map', focusRegions: ['31370', '11010'], mapCommand: { kind: 'zoom', codes: ['31370', '11010'] } });
  });

  it('계절 기간이 아니면 배지가 없다', () => {
    const { wrapper } = serverState([[seasonKeys.current(), seasons([])]]);
    render(<SeasonBadge />, { wrapper });
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('강조한 회차 지역은 다른 지역을 고르면 풀린다', () => {
    useUiStore.getState().showRegionsOnMap(['31370', '11010']);
    useUiStore.getState().select('11020');
    expect(useUiStore.getState().focusRegions).toEqual([]);
  });
});

const stampStatus = (overrides: Partial<StampStatusResponse> = {}): StampStatusResponse => ({
  regionCode: 'KR-31370', regionName: '가평군', painted: true, firstYear: 2026, year: 2027, stampedYears: [], canStamp: true, reason: null, availableFromYear: null, xp: 10, ...overrides,
});

describe('재방문 도장', () => {
  describe('받을 수 있는지 안내', () => {
    it('처음 칠한 해보다 뒤의 해면 "다시 다녀왔어요"로 도장과 XP 를 받을 수 있다', () => {
      expect(revisitView(stampStatus())).toMatchObject({ canStamp: true, label: '다시 다녀왔어요 (+10 XP)', why: null, since: '2026년에 처음 칠함' });
    });

    it('아직 칠하지 않은 곳에는 도장 칸이 없다', () => {
      expect(revisitView(stampStatus({ painted: false, firstYear: null, canStamp: false, reason: 'NOT_PAINTED' }))).toBeNull();
      expect(revisitView(undefined)).toBeNull();
    });

    it('처음 칠한 해와 같은 해면 언제부터 받을 수 있는지 알려 준다', () => {
      expect(revisitView(stampStatus({ year: 2026, canStamp: false, reason: 'SAME_YEAR', availableFromYear: 2027 }))?.why)
        .toBe('2026년에 처음 칠한 곳이에요 — 2027년부터 도장을 받을 수 있어요');
    });

    it('올해 도장을 이미 받았으면 내년에 다시 받을 수 있다고 알려 준다', () => {
      expect(revisitRefusalText(stampStatus({ stampedYears: [2027], canStamp: false, reason: 'ALREADY_STAMPED', availableFromYear: 2028 })))
        .toBe('2027년 도장은 이미 받았어요 — 2028년에 다시 받을 수 있어요');
    });

    it('오늘 칠하기와 도장을 합쳐 하루 상한에 닿았으면 내일 받을 수 있다고 알려 준다', () => {
      expect(revisitRefusalText(stampStatus({ canStamp: false, reason: 'DAILY_CAP' }))).toBe('오늘은 칠하기와 도장을 합쳐 하루 상한에 닿았어요 — 내일 다시 받을 수 있어요');
    });

    it('칠하지 않은 곳이라는 이유도 문장으로 알려 준다', () => {
      expect(revisitRefusalText(stampStatus({ painted: false, canStamp: false, reason: 'NOT_PAINTED' }))).toBe('칠한 곳에서만 재방문 도장을 받을 수 있어요');
    });
  });

  describe('지역 상세', () => {
    it('받은 도장 연도를 차례로 보여 주고, 못 받는 때는 버튼을 막고 이유를 보여 준다', () => {
      const { wrapper } = serverState([[revisitKeys.status('31370'), stampStatus({ stampedYears: [2027, 2028], year: 2028, canStamp: false, reason: 'ALREADY_STAMPED', availableFromYear: 2029 })]]);
      render(<RevisitStamp code="31370" name="가평군" />, { wrapper });
      expect([...document.querySelectorAll('#d-stamps .stamp')].map(stamp => stamp.getAttribute('data-year'))).toEqual(['2027', '2028']);
      expect((screen.getByRole('button') as HTMLButtonElement).disabled).toBe(true);
      expect(document.querySelector('#d-revisit-why')?.textContent).toBe('2028년 도장은 이미 받았어요 — 2029년에 다시 받을 수 있어요');
    });

    it('도장이 아직 없으면 없다고 보여 준다', () => {
      const { wrapper } = serverState([[revisitKeys.status('31370'), stampStatus()]]);
      render(<RevisitStamp code="31370" name="가평군" />, { wrapper });
      expect(screen.getByText('아직 재방문 도장이 없어요')).toBeTruthy();
    });

    it('누르면 도장을 받고, 받은 해와 XP·몇 번째 도장인지 알린다', async () => {
      const { wrapper } = serverState([[revisitKeys.status('31370'), stampStatus()]]);
      render(<RevisitStamp code="31370" name="가평군" />, { wrapper });
      fireEvent.click(screen.getByRole('button', { name: '다시 다녀왔어요 (+10 XP)' }));
      await waitFor(() => expect(useToastStore.getState().toasts.map(toast => toast.title)).toContain('재방문 도장 · 2027'));
      expect(vi.mocked(explorationApi.stamp)).toHaveBeenCalledWith('31370');
      expect(useToastStore.getState().toasts[0].sub).toBe('가평군에 다시 다녀왔어요 · +10 XP · 도장 1개째');
    });

    it('누르는 사이 하루 상한에 닿아 거절되면 오류 안내를 보여 준다', async () => {
      vi.mocked(explorationApi.stamp).mockRejectedValueOnce(new ApiError(422, 'DAILY_CAP_EXCEEDED', 'cap'));
      const { wrapper } = serverState([[revisitKeys.status('31370'), stampStatus()]]);
      render(<RevisitStamp code="31370" name="가평군" />, { wrapper });
      fireEvent.click(screen.getByRole('button'));
      await waitFor(() => expect(useToastStore.getState().toasts.map(toast => toast.title)).toContain('오늘은 여기까지'));
      expect(useToastStore.getState().toasts[0].sub).toBe('오늘은 칠하기와 도장을 합쳐 하루 상한에 닿았어요 — 내일 다시 눌러 주세요');
    });
  });

  describe('도장을 받은 뒤', () => {
    it('XP·뱃지·색 변형이 늦게 반영되니 반영 대기 창을 열고 진행·판정을 다시 읽으며 알림을 켠다', async () => {
      const { wrapper, stale } = serverState([[[SETTLED_ROOT, 'progress'], { xp: 0 }], [revisitKeys.status('31370'), stampStatus()]]);
      const { result } = renderHook(() => useStamp(), { wrapper });
      await act(() => result.current.mutateAsync('31370'));
      expect(stale([SETTLED_ROOT, 'progress'])).toBe(true);
      expect(stale(revisitKeys.status('31370'))).toBe(true);
      expect(useSyncStore.getState().announce).toBe(true);
    });

    it('알림 문구는 받은 해·지역·XP·도장 수로 만든다', () => {
      expect(stampedToast({ regionCode: 'KR-32060', regionName: '속초시', year: 2027, firstYear: 2026, stampedAt: '', xp: 10, stampCount: 5 }))
        .toEqual({ title: '재방문 도장 · 2027', sub: '속초시에 다시 다녀왔어요 · +10 XP · 도장 5개째' });
    });
  });

  describe('거절 안내', () => {
    it('오류 코드마다 제목과 안내 문장이 따로 있다', () => {
      expect(errorTitle('REVISIT_NOT_PAINTED')).toBe('아직 칠하지 않은 곳');
      expect(errorTitle('REVISIT_SAME_YEAR')).toBe('내년부터 받을 수 있어요');
      expect(errorTitle('REVISIT_ALREADY_STAMPED')).toBe('올해 도장은 받았어요');
      expect(stampErrorText('REVISIT_NOT_PAINTED', '가평군')).toBe('가평군 — 아직 칠하지 않은 곳이라 도장을 받을 수 없어요');
      expect(stampErrorText('REVISIT_SAME_YEAR', '가평군')).toBe('처음 칠한 해에는 도장을 받을 수 없어요 — 다음 해부터 받을 수 있어요');
      expect(stampErrorText('REVISIT_ALREADY_STAMPED', '가평군')).toBe('올해 가평군 도장은 이미 받았어요 — 내년에 다시 받을 수 있어요');
    });

    it('도장과 상관없는 오류는 서버 메시지를 그대로 쓴다', () => {
      expect(stampErrorText('HTTP_500', '가평군')).toBeNull();
    });
  });
});

describe('가고 싶은 곳', () => {
  const wish = (regionCode: string, status: 'WANTED' | 'VISITED', regionName = '가평군') =>
    ({ regionCode, regionName, provinceCode: 'KR-31', status, pinnedAt: '2026-10-04T00:00:00Z', fulfilledAt: status === 'VISITED' ? '2026-10-04T01:00:00Z' : null });
  const wishlist = (items: ReturnType<typeof wish>[] = [], overrides: Partial<WishlistResponse> = {}): WishlistResponse => ({
    max: 30, pendingCount: items.filter(item => item.status === 'WANTED').length, fulfilledCount: items.filter(item => item.status === 'VISITED').length, xpPerWish: 20, items, ...overrides,
  });

  describe('가고 싶어요 토글', () => {
    it('아직 칠하지 않은 곳은 꽂을 수 있다', () => {
      expect(wishToggle('KR-31370', wishlist(), false)).toMatchObject({ state: 'open', pressed: false, disabled: false, label: '☆ 가고 싶어요', hint: null });
    });

    it('꽂은 곳은 눌린 채로 칠하면 받을 XP 를 알려 주고, 다시 누르면 뺄 수 있다', () => {
      expect(wishToggle('KR-31370', wishlist([wish('KR-31370', 'WANTED')]), false))
        .toMatchObject({ state: 'pinned', pressed: true, disabled: false, label: '★ 가고 싶은 곳', hint: '칠하면 다녀옴 +20 XP · 다시 누르면 빼요' });
    });

    it('이미 칠한 곳은 꽂을 수 없고 대신 재방문 도장을 안내한다', () => {
      expect(wishToggle('KR-31370', wishlist(), true)).toMatchObject({ state: 'painted', disabled: true, hint: '이미 칠한 곳이에요 — 다시 가면 "다시 다녀왔어요"로 도장을 받을 수 있어요' });
    });

    it('아직 다녀오지 않은 곳이 상한만큼 꽂혀 있으면 더 꽂을 수 없다고 알려 준다', () => {
      const full = wishlist([wish('KR-11010', 'WANTED', '종로구')], { max: 1 });
      expect(wishToggle('KR-31370', full, false)).toMatchObject({ state: 'full', disabled: true, hint: '가고 싶은 곳은 1곳까지 꽂을 수 있어요 — 다녀오거나 빼면 다시 꽂을 수 있어요' });
    });

    it('다녀온 핀은 상한에 세지 않는다', () => {
      const done = wishlist([wish('KR-11010', 'VISITED', '종로구')], { max: 1 });
      expect(wishToggle('KR-31370', done, false).state).toBe('open');
    });

    it('꽂은 뒤 칠해 다녀온 곳은 다녀왔다고 보여 준다', () => {
      expect(wishToggle('KR-31370', wishlist([wish('KR-31370', 'VISITED')]), true)).toMatchObject({ state: 'visited', disabled: true, label: '✓ 가고 싶던 곳 — 다녀왔어요' });
    });

    it('누르면 꽂고, 서버가 돌려준 목록으로 바로 바꾼다', async () => {
      const after = wishlist([wish('KR-31370', 'WANTED')]);
      vi.mocked(explorationApi.pin).mockResolvedValueOnce(after);
      const { wrapper, queryClient } = serverState([[wishlistKeys.list(), wishlist()], [revisitKeys.status('31370'), stampStatus({ painted: false, firstYear: null, canStamp: false, reason: 'NOT_PAINTED' })]]);
      render(<WishToggle code="31370" name="가평군" />, { wrapper });
      fireEvent.click(screen.getByRole('button', { name: '☆ 가고 싶어요' }));
      await waitFor(() => expect(useToastStore.getState().toasts.map(toast => toast.title)).toContain('가고 싶은 곳에 꽂았어요'));
      expect(vi.mocked(explorationApi.pin)).toHaveBeenCalledWith('31370');
      expect(queryClient.getQueryData(wishlistKeys.list())).toEqual(after);
    });

    it('누르는 사이 상한이 차 거절되면 안내하고 목록을 다시 읽는다', async () => {
      vi.mocked(explorationApi.pin).mockRejectedValueOnce(new ApiError(422, 'WISHLIST_FULL', 'full'));
      const { wrapper, stale } = serverState([[wishlistKeys.list(), wishlist()], [revisitKeys.status('31370'), stampStatus({ painted: false, firstYear: null, canStamp: false, reason: 'NOT_PAINTED' })]]);
      render(<WishToggle code="31370" name="가평군" />, { wrapper });
      fireEvent.click(screen.getByRole('button'));
      await waitFor(() => expect(useToastStore.getState().toasts.map(toast => toast.title)).toContain('가고 싶은 곳이 가득해요'));
      expect(useToastStore.getState().toasts[0].sub).toBe('가고 싶은 곳은 30곳까지 꽂을 수 있어요 — 다녀오거나 빼면 다시 꽂을 수 있어요');
      expect(stale(wishlistKeys.list())).toBe(true);
    });

    it('이미 칠한 곳이라 거절되면 재방문 도장을 안내한다', () => {
      expect(errorTitle('WISH_ALREADY_VISITED')).toBe('이미 다녀온 곳');
      expect(errorTitle('WISHLIST_FULL')).toBe('가고 싶은 곳이 가득해요');
      expect(pinErrorText('WISH_ALREADY_VISITED', 30)).toBe('이미 칠한 곳이에요 — 다시 가면 "다시 다녀왔어요"로 도장을 받을 수 있어요');
      expect(pinErrorText('HTTP_500', 30)).toBeNull();
    });

    it('꽂은 곳을 다시 누르면 뺀다', async () => {
      const { wrapper, stale } = serverState([[wishlistKeys.list(), wishlist([wish('KR-31370', 'WANTED')])], [revisitKeys.status('31370'), stampStatus({ painted: false, firstYear: null })]]);
      render(<WishToggle code="31370" name="가평군" />, { wrapper });
      fireEvent.click(screen.getByRole('button', { name: '★ 가고 싶은 곳' }));
      await waitFor(() => expect(useToastStore.getState().toasts.map(toast => toast.title)).toContain('가고 싶은 곳에서 뺐어요'));
      expect(vi.mocked(explorationApi.unpin)).toHaveBeenCalledWith('31370');
      expect(stale(wishlistKeys.list())).toBe(true);
    });
  });

  describe('가고 싶은 곳 목록', () => {
    it('아직 가지 않은 곳과 다녀온 곳을 나누고, 남은 곳 수를 상한과 함께 보여 준다', () => {
      const sections = wishSections(wishlist([wish('KR-31370', 'WANTED'), wish('KR-11010', 'VISITED', '종로구')]));
      expect(sections.summary).toBe('1 / 30');
      expect(sections.pending.map(item => item.regionName)).toEqual(['가평군']);
      expect(sections.visited.map(item => item.regionName)).toEqual(['종로구']);
    });

    it('지도 핀은 아직 다녀오지 않은 곳에만 꽂는다', () => {
      expect(pendingWishCodes(wishlist([wish('KR-31370', 'WANTED'), wish('KR-11010', 'VISITED', '종로구')]))).toEqual(['KR-31370']);
      expect(pendingWishCodes(undefined)).toEqual([]);
    });

    it('목록의 지역을 누르면 지도에서 그 지역을 보여 준다', () => {
      const { wrapper } = serverState([[wishlistKeys.list(), wishlist([wish('KR-31370', 'WANTED'), wish('KR-11010', 'VISITED', '종로구')])]]);
      render(<WishlistCard />, { wrapper });
      expect(document.querySelector('#wish-count')?.textContent).toBe('1 / 30');
      fireEvent.click(screen.getByRole('button', { name: '✓ 종로구' }));
      expect(useUiStore.getState()).toMatchObject({ selected: '11010', highlight: '11010' });
    });

    it('아직 꽂은 곳이 없으면 꽂는 방법과 받을 XP 를 안내한다', () => {
      const { wrapper } = serverState([[wishlistKeys.list(), wishlist()]]);
      render(<WishlistCard />, { wrapper });
      expect(screen.getByText(/"가고 싶어요"를 누르면 여기 모여요/).textContent).toContain('+20 XP');
    });
  });

  describe('꽂은 뒤 거절되면', () => {
    it('목록과 칠했는지 판정을 다시 읽어 토글을 서버에 맞춘다', async () => {
      vi.mocked(explorationApi.pin).mockRejectedValueOnce(new ApiError(409, 'WISH_ALREADY_VISITED', 'visited'));
      const { wrapper, stale } = serverState([[wishlistKeys.list(), wishlist()], [revisitKeys.status('31370'), stampStatus()]]);
      const { result } = renderHook(() => usePin(), { wrapper });
      await act(() => result.current.mutateAsync('31370').catch(() => undefined));
      expect(stale(wishlistKeys.list())).toBe(true);
      expect(stale(revisitKeys.status('31370'))).toBe(true);
    });
  });
});
