/**
 * 지도 옮겨 가기 — 지도 목록을 새로 읽은 뒤 그 지도를 지금 보는 지도로 둔다.
 */
import { afterEach, describe, expect, it } from 'vitest';
import { useUiStore } from '../../store/uiStore';
import { serverState } from '../../test/serverState';
import { mapKeys, switchToMap } from './territory';

const myMaps = () => serverState([[mapKeys.maps(), [{ mapId: 'mine', kind: 'PERSONAL', name: '나의 영토' }, { mapId: 'crew', kind: 'SHARED', name: '원정대' }]]]);

afterEach(() => useUiStore.setState({ mapId: null }));

describe('지도 옮겨 가기', () => {
  it('공유 지도로 옮기면 그 지도를 지금 보는 지도로 두고 지도 목록을 새로 읽는다', async () => {
    const { queryClient, stale } = myMaps();
    await switchToMap(queryClient, 'crew');
    expect(useUiStore.getState().mapId).toBe('crew');
    expect(stale(mapKeys.maps())).toBe(true);
  });

  it('개인 지도로 옮기면 따로 고른 지도 없이 기본(개인 지도)으로 둔다', async () => {
    useUiStore.setState({ mapId: 'crew' });
    const { queryClient } = myMaps();
    await switchToMap(queryClient, 'mine');
    expect(useUiStore.getState().mapId).toBeNull();
  });
});
