import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SETTLED_ROOT, settleInterval, SETTLE_INTERVAL_MS, useSyncStore } from '../../store/syncStore';
import { useUiStore } from '../../store/uiStore';
import { mapKeys } from '../../shared/queries/territory';
import { recapKeys } from '../../shared/queries/recap';
import { useCancelVisit, useCheckIn, useEditVisit, useSeed } from './queries';

vi.mock('../../api/exploration', () => ({
  explorationApi: {
    checkIn: vi.fn(async () => ({ mapId: 'personal', nth: 1, xp: { lines: [], total: 35, basis: 'MAP_MAX', note: '' }, items: [] })),
    cancelVisit: vi.fn(async () => null),
    editVisit: vi.fn(async () => null),
  },
}));
vi.mock('../../api/dev', () => ({ devApi: { seed: vi.fn(async () => ({ seeded: 45 })), clearVisits: vi.fn() } }));

function setup() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  // 관찰 중인 쿼리처럼 캐시에 넣어 둔다
  queryClient.setQueryData(mapKeys.territory(null), { mapId: 'personal' });
  queryClient.setQueryData([SETTLED_ROOT, 'progress'], { xp: 0 });
  queryClient.setQueryData(['social', 'friends'], { people: [] });
  queryClient.setQueryData(recapKeys.recap(2026, null), { newRegions: 0 });
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  return { queryClient, wrapper, invalidate };
}

afterEach(() => {
  useSyncStore.setState({ settleUntil: 0, announce: false });
  useUiStore.setState({ sampleMode: false });
});

describe('체크인 뮤테이션', () => {
  it('성공하면 영토(+지도 상세)를 무효화하고, 반영 대기 창을 열어 진행·가방(settled)을 무효화한다 — 알림 켬', async () => {
    const { queryClient, wrapper, invalidate } = setup();
    const { result } = renderHook(() => useCheckIn(), { wrapper });
    await act(() => result.current.mutateAsync({ code: '11010', visitDate: '2026-10-03', memo: '', photoUrl: '', mapId: null }));
    const keys = invalidate.mock.calls.map(([filters]) => JSON.stringify(filters?.queryKey));
    expect(keys).toEqual(expect.arrayContaining([JSON.stringify(mapKeys.territories()), JSON.stringify(mapKeys.details()), JSON.stringify([SETTLED_ROOT])]));
    expect(keys).not.toContain(JSON.stringify(['social']));
    expect(queryClient.getQueryState(mapKeys.territory(null))?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState([SETTLED_ROOT, 'progress'])?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(['social', 'friends'])?.isInvalidated).toBe(false);
    // 반영 대기 창 동안만 짧은 주기로 다시 읽는다(setTimeout 체인 없음)
    expect(settleInterval()).toBe(SETTLE_INTERVAL_MS);
    expect(useSyncStore.getState().announce).toBe(true);
  });

  it('반영 대기 창이 지나면 주기 재조회를 끈다', () => {
    useSyncStore.setState({ settleUntil: Date.now() - 1 });
    expect(settleInterval()).toBe(false);
  });

  it('예시 채우기는 알림 없이 반영하고 예시 모드를 켠다', async () => {
    const { wrapper } = setup();
    const { result } = renderHook(() => useSeed(), { wrapper });
    await act(() => result.current.mutateAsync());
    await waitFor(() => expect(useUiStore.getState().sampleMode).toBe(true));
    expect(useSyncStore.getState().announce).toBe(false);
  });
});

describe('리캡(GET /me/recap) 무효화', () => {
  const recapInvalidated = (queryClient: QueryClient) => queryClient.getQueryState(recapKeys.recap(2026, null))?.isInvalidated;

  it('체크인 뒤 리캡을 다시 읽는다', async () => {
    const { queryClient, wrapper } = setup();
    const { result } = renderHook(() => useCheckIn(), { wrapper });
    await act(() => result.current.mutateAsync({ code: '11010', visitDate: '2026-10-03', memo: '', photoUrl: '', mapId: null }));
    expect(recapInvalidated(queryClient)).toBe(true);
  });

  it('취소 뒤 리캡을 다시 읽는다', async () => {
    const { queryClient, wrapper } = setup();
    const { result } = renderHook(() => useCancelVisit(), { wrapper });
    await act(() => result.current.mutateAsync({ code: '11010', mapId: null }));
    expect(recapInvalidated(queryClient)).toBe(true);
  });

  it('기록 수정(날짜가 바뀌면 달이 바뀐다) 뒤 리캡을 다시 읽는다', async () => {
    const { queryClient, wrapper } = setup();
    const { result } = renderHook(() => useEditVisit(), { wrapper });
    await act(() => result.current.mutateAsync({ code: '11010', visitDate: '2026-03-01', memo: '', mapId: null }));
    expect(recapInvalidated(queryClient)).toBe(true);
  });
});
