import { useEffect, useRef } from 'react';
import { apiClient, type ApiError } from '../api/client';
import { useSession } from '../shared/queries/session';
import { useJoinViaProfile } from '../features/sharedMap/queries';
import { useMyTerritory } from '../shared/queries/territory';
import { noticeText } from '../features/profile/model/account';
import { useSyncStore } from '../store/syncStore';
import { toast, toastError } from '../store/toastStore';
import { useUiStore } from '../store/uiStore';

const MAP_GONE: readonly string[] = ['NOT_A_MEMBER', 'MAP_NOT_FOUND'];

/** 새 익명 탐험가 발급·로그아웃 → 지금 보는 지도를 개인 지도로 */
export function useIdentityReset(): void {
  useEffect(() => apiClient.onIdentityReset(() => useUiStore.getState().setMapId(null)), []);
}

/** 보던 공유 지도에서 빠졌거나 지도가 사라졌으면 개인 지도로(조용히), 그 밖의 실패는 토스트 */
export function useTerritoryFallback(): void {
  const { mapId, territoryQuery } = useMyTerritory();
  const { error } = territoryQuery;
  useEffect(() => {
    if (!error) return;
    if (mapId && MAP_GONE.includes((error as ApiError).code)) useUiStore.getState().setMapId(null);
    else toastError(error);
  }, [error, mapId]);
}

/** 로그인 직후 안내(연결·병합) 토스트 — 병합은 방문 이동·재계산이 비동기라 잠시 영토·진행을 다시 읽는다 */
export function useLoginNotice(): void {
  const { data: session } = useSession();
  const shown = useRef(false);
  useEffect(() => {
    const notice = session?.mergeNotice;
    const text = noticeText(notice);
    if (shown.current || !notice || !text) return;
    shown.current = true;
    toast('✓', '로그인했어요', text);
    if (notice.outcome === 'MERGED') useSyncStore.getState().beginTerritory();
  }, [session]);
}

/** 공개 프로필의 "이 지도에 합류"(/?joinProfile={handle}&map={mapId}) — 지금 탐험가로 합류하고 그 지도로 옮긴다 */
export function useProfileJoin(): void {
  const { isFetched } = useSession();
  const join = useJoinViaProfile();
  const started = useRef(false);
  useEffect(() => {
    if (!isFetched || started.current) return;
    const query = new URLSearchParams(location.search);
    const handle = query.get('joinProfile');
    const mapId = query.get('map');
    if (!handle || !mapId) return;
    started.current = true;
    history.replaceState(null, '', location.pathname + location.hash);
    join.mutateAsync({ handle, mapId })
      .then(joined => {
        toast('✓', '지도에 합류했어요', `@${handle}의 ${joined.name}`);
        document.documentElement.dataset.joinedVia = 'profile';
      })
      .catch(error => toastError(error));
  }, [isFetched, join]);
}
