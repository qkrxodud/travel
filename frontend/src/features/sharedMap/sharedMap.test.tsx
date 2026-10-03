/**
 * 공유 지도 — 친구들과 한 지도를 만들고, 초대코드로 합류하고, 지도장이 설정을 바꾸고, 나간다. 멤버·선점·설정은 서버 값이다.
 * 이야기 순서: 만들기 → 합류 → 다시 합류 → 지도장 설정·초대코드 → 나가기.
 */
import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { MapSettings } from '../../api/types/exploration';
import { mapKeys } from '../../shared/queries/territory';
import { SETTLE_INTERVAL_MS, settleInterval, useSyncStore } from '../../store/syncStore';
import { useUiStore } from '../../store/uiStore';
import { serverState } from '../../test/serverState';
import { useCreateMap, useJoinMap, useLeaveMap, useRegenerateInvite, useSaveSettings } from './queries';

const { JOINED } = vi.hoisted(() => ({ JOINED: { mapId: 'crew', rejoined: false } }));
const PRIVATE_SETTINGS: MapSettings = { photoRequired: false, dailyCheckInCap: 5, visibility: 'PRIVATE' };

vi.mock('../../api/exploration', () => ({
  explorationApi: {
    createMap: vi.fn(async () => ({ mapId: 'crew', inviteCode: 'ABCDEFGH' })),
    joinMap: vi.fn(async () => ({ ...JOINED })),
    leaveMap: vi.fn(async () => null),
    regenerateInvite: vi.fn(async () => ({ mapId: 'crew', inviteCode: 'NEWCODE1' })),
    saveSettings: vi.fn(async (mapId: string, settings: unknown) => ({ mapId, inviteCode: 'ABCDEFGH', settings })),
  },
}));

/** 내 지도 목록(개인 지도 + 원정대)을 이미 읽은 화면 */
const myMaps = () => serverState([
  [mapKeys.maps(), [{ mapId: 'mine', kind: 'PERSONAL', name: '나의 영토' }, { mapId: 'crew', kind: 'SHARED', name: '원정대' }]],
  [mapKeys.territory('crew'), { mapId: 'crew' }],
  [mapKeys.detail('crew'), { mapId: 'crew', inviteCode: 'ABCDEFGH', settings: PRIVATE_SETTINGS }],
]);

afterEach(() => {
  JOINED.rejoined = false;
  useUiStore.setState({ mapId: null, selected: null, highlight: null });
  useSyncStore.setState({ settleUntil: 0, territoryUntil: 0, announce: false });
});

describe('공유 지도 만들기', () => {
  it('만들면 지도 목록을 다시 읽고 바로 새 지도로 옮겨 간다', async () => {
    const { wrapper, stale } = myMaps();
    const { result } = renderHook(() => useCreateMap(), { wrapper });
    await act(() => result.current.mutateAsync('원정대'));
    expect(stale(mapKeys.maps())).toBe(true);
    expect(useUiStore.getState().mapId).toBe('crew');
  });
});

describe('초대코드로 합류', () => {
  it('합류하면 그 지도로 옮겨 간다', async () => {
    const { wrapper } = myMaps();
    const { result } = renderHook(() => useJoinMap(), { wrapper });
    await act(() => result.current.mutateAsync('ABCDEFGH'));
    expect(useUiStore.getState().mapId).toBe('crew');
    expect(settleInterval()).toBe(false);
  });

  it('나갔던 지도에 다시 합류하면 서버가 예전 방문을 늦게 되살리므로 잠깐 동안 영토를 다시 읽는다', async () => {
    JOINED.rejoined = true;
    const { wrapper, stale } = myMaps();
    const { result } = renderHook(() => useJoinMap(), { wrapper });
    await act(() => result.current.mutateAsync('ABCDEFGH'));
    expect(settleInterval()).toBe(SETTLE_INTERVAL_MS);
    await vi.waitFor(() => expect(stale(mapKeys.territory('crew'))).toBe(true));
  });
});

describe('지도장 설정과 초대코드', () => {
  it('설정을 저장하면 서버가 돌려준 지도 정보로 바로 바꾼다', async () => {
    const { queryClient, wrapper } = myMaps();
    const { result } = renderHook(() => useSaveSettings(), { wrapper });
    await act(() => result.current.mutateAsync({ mapId: 'crew', settings: { ...PRIVATE_SETTINGS, photoRequired: true } }));
    expect(queryClient.getQueryData(mapKeys.detail('crew'))).toMatchObject({ settings: { photoRequired: true } });
  });

  it('초대코드를 다시 만들면 예전 코드 대신 새 코드를 보여 준다', async () => {
    const { queryClient, wrapper } = myMaps();
    const { result } = renderHook(() => useRegenerateInvite(), { wrapper });
    await act(() => result.current.mutateAsync('crew'));
    expect(queryClient.getQueryData(mapKeys.detail('crew'))).toMatchObject({ inviteCode: 'NEWCODE1' });
  });
});

describe('공유 지도 나가기', () => {
  it('나가면 개인 지도로 돌아오고 지도 목록을 다시 읽는다', async () => {
    useUiStore.setState({ mapId: 'crew', selected: '11010' });
    const { wrapper, stale } = myMaps();
    const { result } = renderHook(() => useLeaveMap(), { wrapper });
    await act(() => result.current.mutateAsync('crew'));
    expect(useUiStore.getState()).toMatchObject({ mapId: null, selected: null });
    expect(stale(mapKeys.maps())).toBe(true);
  });
});
