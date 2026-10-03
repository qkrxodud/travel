/**
 * 지도 탭 — 내 영토를 보고, 지역을 눌러 칠하고(체크인), 고치고, 지운다. 정복률·시·도별 값은 서버 값이고 화면은 고르기·정렬·문구만 한다.
 * 이야기 순서: 내 영토 보기 → 지역 누르기 → 체크인 모달 → 체크인한 뒤 → 기록 수정·취소 → 예시 채우기·전부 지우기 → 공유 지도 이의.
 */
import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { explorationApi } from '../../api/exploration';
import type { MapDetailResponse } from '../../api/types/exploration';
import type { SetResponse } from '../../api/types/progression';
import { myVisits, type MyVisit } from '../../shared/lib/territory/visits';
import { recapKeys } from '../../shared/queries/recap';
import { mapKeys } from '../../shared/queries/territory';
import { SETTLED_ROOT, settleInterval, SETTLE_INTERVAL_MS, useSyncStore } from '../../store/syncStore';
import { useToastStore } from '../../store/toastStore';
import { useUiStore } from '../../store/uiStore';
import { CATALOG, territory, visit } from '../../test/fixtures';
import { serverState } from '../../test/serverState';
import { claimMaps, latestVisitCode, localIsoDate, logEntries, provinceRows, regionTip, setChips, showsProvinceFirstHint } from './model/territory';
import { useCancelVisit, useCheckIn, useClearVisits, useDispute, useEditVisit, useMapActions, useSeed } from './queries';

vi.mock('../../api/exploration', () => ({
  explorationApi: {
    checkIn: vi.fn(async () => ({ mapId: 'personal', nth: 1, xp: { lines: [], total: 35, basis: 'MAP_MAX', note: '' }, items: [] })),
    cancelVisit: vi.fn(async () => null),
    editVisit: vi.fn(async () => null),
    dispute: vi.fn(async () => null),
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
