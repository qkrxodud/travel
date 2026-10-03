/** 진행(2단계): XP·레벨·칭호·스트릭·뱃지·도감·퀘스트 — 체크인 후 이벤트로 비동기 반영된다. */
import { apiClient } from './client';
import type { CollectionResponse, ProgressResponse, QuestClaimResponse, QuestsResponse } from './types/progression';

export const progressionApi = {
  progress: () => apiClient.request<ProgressResponse>('GET', '/progress'),
  collection: () => apiClient.request<CollectionResponse>('GET', '/collection'),
  quests: () => apiClient.request<QuestsResponse>('GET', '/quests'),
  claim: (questId: string) => apiClient.request<QuestClaimResponse>('POST', '/quests/' + encodeURIComponent(questId) + '/claim'),
  selectTitle: (titleId: string) => apiClient.request<ProgressResponse>('PUT', '/progress/title', { titleId }),
};
