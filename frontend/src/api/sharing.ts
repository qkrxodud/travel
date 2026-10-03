/** 공유(4단계): 내 카드(서버 PNG)·공개 범위·공개 프로필 경로 · 연간 리캡 JSON(06) */
import { apiClient } from './client';
import type { CardKind, MyCardsResponse, PrivacyResponse, ProfileVisibility, RecapResponse } from './types/sharing';

export const sharingApi = {
  myCards: () => apiClient.request<MyCardsResponse>('GET', '/me/cards'),
  /** 내 카드 PNG(인증 필요) → blob: URL. 다 쓰면 URL.revokeObjectURL 로 놓아 준다. */
  cardImageUrl: async (kind: CardKind) => URL.createObjectURL(await apiClient.image('/me/cards/' + kind + '.png')),
  /** 연간 리캡(본인 전용). mapId 를 생략(null)하면 개인 지도 */
  recap: (year: number, mapId: string | null) =>
    apiClient.request<RecapResponse>('GET', '/me/recap?year=' + year + (mapId ? '&mapId=' + encodeURIComponent(mapId) : '')),
  setPrivacy: (visibility: ProfileVisibility) => apiClient.request<PrivacyResponse>('PUT', '/me/privacy', { visibility }),
  /** 공개 VS 카드 PNG 주소(로그인·공개 프로필 필요 — <img src> 로 바로 연다) */
  vsCardPath: (myHandle: string, otherHandle: string) =>
    `/u/${encodeURIComponent(myHandle)}/vs/${encodeURIComponent(otherHandle)}.png`,
};
