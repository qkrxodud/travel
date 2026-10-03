import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import type { ApiError } from '../../api/client';
import { socialApi } from '../../api/social';
import { toastError } from '../../store/toastStore';
import { useUiStore } from '../../store/uiStore';
import { normalizeHandle } from '../../shared/lib/handle';
import { socialKeys } from '../../shared/queries/social';

/** 랭킹 탭을 열 때마다 다시 읽는다(프로토타입 loadSocial 과 같게). */
export function useFriendRanking(enabled: boolean) {
  return useQuery({ queryKey: socialKeys.friendRanking(), queryFn: socialApi.friendRanking, enabled, staleTime: 0 });
}

export function useFriends(enabled: boolean) {
  return useQuery({ queryKey: socialKeys.friends(), queryFn: socialApi.friends, enabled, staleTime: 0 });
}

export function useFeed(enabled: boolean) {
  return useQuery({ queryKey: socialKeys.feed(), queryFn: socialApi.feed, enabled, staleTime: 0 });
}

export function useMapRanking(mapId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: socialKeys.mapRanking(mapId ?? ''),
    queryFn: () => socialApi.mapRanking(mapId as string),
    enabled: enabled && !!mapId,
    staleTime: 0,
    meta: { silent: true },
  });
}

/** 영토 비교 — 서로 팔로우한 친구이거나 공개 프로필만 */
export function useCompare(handle: string | null) {
  return useQuery({
    queryKey: socialKeys.compare(handle ?? ''),
    queryFn: () => socialApi.compare(handle as string),
    enabled: !!handle,
    staleTime: 0,
    meta: { silent: true },
  });
}

export function useFollow() {
  return useMutation({ mutationFn: (handle: string) => socialApi.follow(handle) });
}

export function useUnfollow() {
  return useMutation({ mutationFn: (handle: string) => socialApi.unfollow(handle) });
}

/** 팔로우 관계가 바뀐 뒤 소셜 값 전부 다시 읽기 */
export function useRefreshSocial() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: socialKeys.all() });
}

/** 영토 비교 시작: 비교 결과를 받은 뒤에만 비교 대상을 바꾼다(실패하면 이전 비교 그대로 + 안내). */
export function useStartCompare() {
  const queryClient = useQueryClient();
  return useCallback(async (input: string | null | undefined) => {
    const handle = normalizeHandle(input);
    if (!handle) return;
    try {
      await queryClient.fetchQuery({ queryKey: socialKeys.compare(handle), queryFn: () => socialApi.compare(handle), staleTime: 0, meta: { silent: true } });
      useUiStore.getState().compareWith(handle);
    } catch (error) {
      toastError(error, (error as ApiError).code === 'PROFILE_NOT_FOUND' ? '비교할 수 없어요 — 서로 팔로우한 친구이거나 공개 프로필만 비교돼요.' : undefined);
    }
  }, [queryClient]);
}
