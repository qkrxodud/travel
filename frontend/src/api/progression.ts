/** 진행(2단계): XP·레벨·칭호·스트릭·뱃지·도감·퀘스트·(8단계) 이번 주 미스터리 — 체크인 후 이벤트로 비동기 반영된다. */
import { apiClient } from './client';
import type { CollectionResponse, MysteryWeekResponse, ProgressResponse, QuestClaimResponse, QuestsResponse } from './types/progression';

export const progressionApi = {
  progress: () => apiClient.request<ProgressResponse>('GET', '/progress'),
  collection: () => apiClient.request<CollectionResponse>('GET', '/collection'),
  quests: () => apiClient.request<QuestsResponse>('GET', '/quests'),
  claim: (questId: string) => apiClient.request<QuestClaimResponse>('POST', '/quests/' + encodeURIComponent(questId) + '/claim'),
  /** 이번 주 미스터리 지역(8단계) */
  mysteryThisWeek: () => apiClient.request<MysteryWeekResponse>('GET', '/mystery/this-week'),
  selectTitle: (titleId: string) => apiClient.request<ProgressResponse>('PUT', '/progress/title', { titleId }),
};
