/**
 * 랭킹 탭 — 친구를 이름·색으로 알아보고, 친구 소식을 읽고, 내 영토와 친구 영토를 견준다. 순위·지역 수·비교 결과는 서버 값이다.
 * 이야기 순서: 친구 알아보기 → 친구 소식 → 영토 비교 시작 → 비교 결과 보기 → 팔로우가 바뀐 뒤.
 */
import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { socialApi } from '../../api/social';
import type { CompareResponse, FeedItemResponse } from '../../api/types/social';
import { normalizeHandle } from '../../shared/lib/handle';
import { socialKeys } from '../../shared/queries/social';
import { useToastStore } from '../../store/toastStore';
import { useUiStore } from '../../store/uiStore';
import { CATALOG } from '../../test/fixtures';
import { serverState } from '../../test/serverState';
import { avatarColor, compareClasses, displayHandle, feedParts, initialOf, leadText, regionNames, RELATION_LABEL, relationOf, shareOfAll } from './model/social';
import { useRefreshSocial, useStartCompare } from './queries';

vi.mock('../../api/social', () => ({ socialApi: { compare: vi.fn() } }));

const feed = (overrides: Partial<FeedItemResponse>): FeedItemResponse => ({
  handle: 'lee', kind: 'VISIT', regionCode: null, rarity: null, themeId: null, level: null, badgeId: null, daysAgo: 0, when: '오늘', ...overrides,
});
const COMPARE: CompareResponse = { me: { handle: 'kim', regionCount: 2 }, other: { handle: 'lee', regionCount: 2 }, mutual: true, onlyMine: ['KR-37430'], both: ['KR-11010'], onlyTheirs: ['KR-31370'], lead: 0 };
const noName = () => undefined;

afterEach(() => {
  vi.clearAllMocks();
  useUiStore.setState({ compareHandle: null });
  useToastStore.setState({ toasts: [] });
});

describe('친구 알아보기', () => {
  it('아바타 색은 handle 마다 늘 같고, handle 이 없어도 색이 있다', () => {
    expect(avatarColor('kim')).toBe(avatarColor('kim'));
    expect(avatarColor(null)).toMatch(/^#[0-9a-f]{6}$/);
  });

  it('handle 은 @ 를 붙여 부르고, 아직 없으면 탐험가라고 부른다', () => {
    expect(displayHandle('kim')).toBe('@kim');
    expect(displayHandle(null)).toBe('탐험가');
  });

  it('아바타 글자는 handle 첫 글자를 대문자로, 없으면 대신할 글자를 쓴다', () => {
    expect(initialOf('kim')).toBe('K');
    expect(initialOf(null, 'M')).toBe('M');
  });

  it('친구를 찾을 때 앞뒤 공백과 @ 는 떼고 찾는다', () => {
    expect(normalizeHandle('  @lee_travels ')).toBe('lee_travels');
    expect(normalizeHandle(null)).toBe('');
  });

  it('서로 팔로우하면 친구, 내가 팔로우하면 팔로잉, 상대만 팔로우하면 나를 팔로우로 부른다', () => {
    const label = (following: boolean, follower: boolean) => RELATION_LABEL[relationOf({ handle: 'a', following, follower, mutual: following && follower })];
    expect([label(true, true), label(true, false), label(false, true)]).toEqual(['친구', '팔로잉', '나를 팔로우']);
  });

  it('친구가 칠한 지역 수를 전국 지역 중 몇 % 인지로 보여 준다', () => {
    expect(shareOfAll(45, 250)).toBe(18);
    expect(shareOfAll(3, 0)).toBe(0);
  });
});

describe('친구 소식', () => {
  it('새 지역을 칠하면 지역 이름과 희귀도를 붙여 알려 준다', () => {
    expect(feedParts(feed({ regionCode: 'KR-37430', rarity: 'LEGEND' }), CATALOG, noName, noName)).toEqual({ strong: '경북 울릉군', text: '에 발 도장 · 전설 지역' });
    expect(feedParts(feed({ regionCode: 'KR-31370', rarity: 'RARE' }), CATALOG, noName, noName).text).toBe('에 발 도장 · 희귀 지역');
    expect(feedParts(feed({ regionCode: 'KR-11010', rarity: 'COMMON' }), CATALOG, noName, noName).text).toBe('에 발 도장');
  });

  it('테마 세트를 완성하면 세트 이름으로 알려 준다', () => {
    expect(feedParts(feed({ kind: 'THEME_COMPLETED', themeId: 'jiri' }), CATALOG, () => '지리산 둘레', noName)).toEqual({ strong: '지리산 둘레', text: ' 테마를 완성했어요' });
  });

  it('레벨이 오르면 새 레벨을 알려 준다', () => {
    expect(feedParts(feed({ kind: 'LEVEL_UP', level: 4 }), CATALOG, noName, noName)).toEqual({ strong: 'Lv.4', text: ' 달성' });
  });

  it('뱃지를 얻으면 뱃지 이름을 알려 준다', () => {
    expect(feedParts(feed({ kind: 'BADGE_EARNED', badgeId: 'first' }), CATALOG, noName, () => '첫 발자국').prefix).toBe('뱃지 「첫 발자국」를 얻었어요');
  });
});

describe('영토 비교 시작', () => {
  it('비교 결과를 받은 뒤에야 비교 대상을 바꾼다', async () => {
    vi.mocked(socialApi.compare).mockResolvedValue(COMPARE);
    const { result } = renderHook(() => useStartCompare(), { wrapper: serverState().wrapper });
    await act(() => result.current(' @lee '));
    expect(vi.mocked(socialApi.compare)).toHaveBeenCalledWith('lee');
    expect(useUiStore.getState().compareHandle).toBe('lee');
  });

  it('비교할 수 없는 상대면 이전 비교를 그대로 두고, 친구이거나 공개 프로필만 된다고 알려 준다', async () => {
    useUiStore.setState({ compareHandle: 'kim' });
    vi.mocked(socialApi.compare).mockRejectedValue(new ApiError(404, 'PROFILE_NOT_FOUND', '없음'));
    const { result } = renderHook(() => useStartCompare(), { wrapper: serverState().wrapper });
    await act(() => result.current('stranger'));
    expect(useUiStore.getState().compareHandle).toBe('kim');
    expect(useToastStore.getState().toasts[0]).toMatchObject({ title: '프로필 없음', sub: expect.stringContaining('서로 팔로우한 친구이거나 공개 프로필만') });
  });

  it('handle 을 비워 두면 아무것도 묻지 않는다', async () => {
    const { result } = renderHook(() => useStartCompare(), { wrapper: serverState().wrapper });
    await act(() => result.current('  @ '));
    expect(vi.mocked(socialApi.compare)).not.toHaveBeenCalled();
  });
});

describe('비교 결과 보기', () => {
  it('나만 간 곳·둘 다 간 곳·친구만 간 곳을 다른 색으로 칠한다', () => {
    expect(Object.fromEntries(compareClasses(COMPARE))).toEqual({ 37430: 'vs-mine', 11010: 'vs-both', 31370: 'vs-theirs' });
  });

  it('지역 목록은 여섯 곳까지 이름으로, 나머지는 외 n곳으로 줄인다', () => {
    expect(regionNames(['KR-11010', 'KR-37430'], CATALOG)).toBe('서울 종로구, 경북 울릉군');
    expect(regionNames(Array.from({ length: 8 }, () => 'KR-11010'), CATALOG)).toMatch(/ 외 2곳$/);
  });

  it('지역 수 차이를 앞섬·뒤짐·동점으로 알려 준다', () => {
    expect([leadText(2), leadText(-1), leadText(0)]).toEqual(['2곳 앞섬', '1곳 뒤짐', '동점']);
  });
});

describe('팔로우가 바뀐 뒤', () => {
  it('친구 목록·랭킹·소식·비교를 모두 새로 읽는다', async () => {
    const { wrapper, stale } = serverState([[socialKeys.friends(), []], [socialKeys.feed(), []], [socialKeys.friendRanking(), []]]);
    const { result } = renderHook(() => useRefreshSocial(), { wrapper });
    await act(() => result.current());
    expect([stale(socialKeys.friends()), stale(socialKeys.feed()), stale(socialKeys.friendRanking())]).toEqual([true, true, true]);
  });
});
