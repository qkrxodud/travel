/**
 * 관리자 API(X-Admin-Token) — 토큰은 화면 메모리에만 있다.
 * - 운영 지표(10단계): GET /admin/metrics · POST /admin/metrics/batch
 * - 계절 회차 지역 목록(13s단계): GET /admin/seasons · GET /admin/seasons/{roundId} · POST …/refresh?fresh= · PUT …/confirm
 */
import { apiClient } from './client';
import type { BatchResponse, MetricsResponse } from './types/analytics';
import type { RoundLineupResponse, SeasonLineupsResponse } from './types/catalog';

const roundPath = (roundId: string) => '/admin/seasons/' + encodeURIComponent(roundId);

export const adminApi = {
  metrics: (adminToken: string, days: number) => apiClient.admin<MetricsResponse>('GET', '/admin/metrics?days=' + days, adminToken),
  runBatch: (adminToken: string) => apiClient.admin<BatchResponse>('POST', '/admin/metrics/batch', adminToken),
  seasonLineups: (adminToken: string) => apiClient.admin<SeasonLineupsResponse>('GET', '/admin/seasons', adminToken),
  seasonLineup: (adminToken: string, roundId: string) => apiClient.admin<RoundLineupResponse>('GET', roundPath(roundId), adminToken),
  /** 지금 모아 후보로(확정 아님). fresh = 같은 날 캐시를 건너뛴다(하루 예산 안) */
  refreshSeasonLineup: (adminToken: string, roundId: string, fresh: boolean) =>
    apiClient.admin<RoundLineupResponse>('POST', `${roundPath(roundId)}/refresh?fresh=${fresh}`, adminToken),
  confirmSeasonLineup: (adminToken: string, roundId: string) => apiClient.admin<RoundLineupResponse>('PUT', `${roundPath(roundId)}/confirm`, adminToken),
};
