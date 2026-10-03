/** 공유 지도 만들기·합류·나가기·초대코드·지도장 설정(3단계). 읽기 쿼리·키는 shared/queries/territory. */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { explorationApi } from '../../api/exploration';
import type { MapDetailResponse, MapSettings } from '../../api/types/exploration';
import { mapKeys, switchToMap } from '../../shared/queries/territory';
import { settleAfterChange } from '../../store/syncStore';
import { useUiStore } from '../../store/uiStore';

export function useCreateMap() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => explorationApi.createMap(name),
    onSuccess: created => switchToMap(queryClient, created.mapId),
  });
}

export function useJoinMap() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (inviteCode: string) => explorationApi.joinMap(inviteCode),
    onSuccess: async joined => {
      await switchToMap(queryClient, joined.mapId);
      // 재가입 복구는 이벤트로 비동기 — 영토를 잠깐 더 다시 읽는다
      if (joined.rejoined) void settleAfterChange(queryClient, false).then(() => queryClient.invalidateQueries({ queryKey: mapKeys.territories() }));
    },
  });
}

/** 공개 프로필 링크로 합류(로그인 직후 앱 셸이 부른다) */
export function useJoinViaProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ handle, mapId }: { handle: string; mapId: string }) => explorationApi.joinViaProfile(handle, mapId),
    onSuccess: joined => switchToMap(queryClient, joined.mapId),
  });
}

export function useLeaveMap() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (mapId: string) => explorationApi.leaveMap(mapId),
    onSuccess: async () => {
      useUiStore.getState().setMapId(null);
      await queryClient.invalidateQueries({ queryKey: mapKeys.maps() });
    },
  });
}

export function useRegenerateInvite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (mapId: string) => explorationApi.regenerateInvite(mapId),
    onSuccess: regenerated => queryClient.setQueryData<MapDetailResponse>(mapKeys.detail(regenerated.mapId), detail =>
      detail ? { ...detail, inviteCode: regenerated.inviteCode } : detail),
  });
}

export function useSaveSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ mapId, settings }: { mapId: string; settings: MapSettings }) => explorationApi.saveSettings(mapId, settings),
    onSuccess: detail => queryClient.setQueryData<MapDetailResponse>(mapKeys.detail(detail.mapId), detail),
  });
}
