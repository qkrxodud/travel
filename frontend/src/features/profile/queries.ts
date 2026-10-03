import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { accountApi } from '../../api/account';
import { explorationApi } from '../../api/exploration';
import { progressionApi } from '../../api/progression';
import { sharingApi } from '../../api/sharing';
import type { SessionResponse } from '../../api/types/account';
import type { MapDetailResponse } from '../../api/types/exploration';
import type { ProgressResponse } from '../../api/types/progression';
import type { CardKind, MyCardsResponse, ProfileVisibility } from '../../api/types/sharing';
import { progressKeys } from '../../shared/queries/progress';
import { sessionKeys } from '../../shared/queries/session';
import { mapKeys } from '../../shared/queries/territory';

export const sharingKeys = {
  all: () => ['sharing'] as const,
  cards: () => ['sharing', 'cards'] as const,
  cardImage: (kind: CardKind) => ['sharing', 'card-image', kind] as const,
};

export function useSelectTitle() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (titleId: string) => progressionApi.selectTitle(titleId),
    onSuccess: progress => queryClient.setQueryData<ProgressResponse>(progressKeys.progress(), progress),
  });
}

/** GET /me/cards — 프로필 탭을 열 때마다 다시 읽는다. */
export function useMyCards(enabled: boolean) {
  return useQuery({ queryKey: sharingKeys.cards(), queryFn: sharingApi.myCards, enabled, staleTime: 0, meta: { silent: true } });
}

/** 내 카드 PNG(blob: URL) — 쓰던 URL 은 컴포넌트가 놓아 준다. */
export function useCardImage(kind: CardKind, enabled: boolean) {
  return useQuery({ queryKey: sharingKeys.cardImage(kind), queryFn: () => sharingApi.cardImageUrl(kind), enabled, staleTime: 0, gcTime: 0, meta: { silent: true } });
}

export function useSetPrivacy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (visibility: ProfileVisibility) => sharingApi.setPrivacy(visibility),
    onSuccess: privacy => queryClient.setQueryData<MyCardsResponse>(sharingKeys.cards(), cards =>
      cards ? { ...cards, visibility: privacy.visibility, publiclyVisible: privacy.publiclyVisible } : cards),
  });
}

/** 카드 버튼: 서버 PNG 를 새로 받아 미리보기 모달로(blob: URL) */
export function useOpenServerCard() {
  return useMutation({ mutationFn: (kind: CardKind) => sharingApi.cardImageUrl(kind) });
}

/** 프로필 링크로 합류할 수 있게 공유 지도 공개/숨김(지도장) — 최신 설정을 읽은 뒤 공개 범위만 바꾼다. */
export function useSetMapVisibility() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ mapId, visible }: { mapId: string; visible: boolean }) => {
      const current = await explorationApi.mapDetail(mapId);
      return explorationApi.saveSettings(mapId, { ...current.settings, visibility: visible ? 'PUBLIC' : 'PRIVATE' });
    },
    onSuccess: detail => queryClient.setQueryData<MapDetailResponse>(mapKeys.detail(detail.mapId), detail),
  });
}

export function useChangeHandle() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (handle: string) => explorationApi.changeHandle(handle),
    onSuccess: changed => {
      queryClient.setQueryData<SessionResponse | null>(sessionKeys.session(), session => (session ? { ...session, handle: changed.handle } : session));
    },
  });
}

export function useLoginIntent() {
  return useMutation({ mutationFn: accountApi.loginIntent });
}

/** 로그아웃 → 이 기기는 새 익명 탐험가. 카탈로그 말고 서버 상태를 전부 버리고 다시 읽는다. */
export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: accountApi.logout,
    onSuccess: async () => {
      queryClient.removeQueries({ predicate: query => query.queryKey[0] !== 'catalog' && query.queryKey[0] !== 'auth' });
      await queryClient.invalidateQueries({ queryKey: sessionKeys.session() });
      await queryClient.invalidateQueries({ predicate: query => query.queryKey[0] !== 'catalog' });
    },
  });
}
