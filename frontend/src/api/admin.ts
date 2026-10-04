/** 운영 지표(10단계): X-Admin-Token 으로 GET /admin/metrics · POST /admin/metrics/batch. 토큰은 화면 메모리에만 있다. */
import { apiClient } from './client';
import type { BatchResponse, MetricsResponse } from './types/analytics';

export const adminApi = {
  metrics: (adminToken: string, days: number) => apiClient.admin<MetricsResponse>('GET', '/admin/metrics?days=' + days, adminToken),
  runBatch: (adminToken: string) => apiClient.admin<BatchResponse>('POST', '/admin/metrics/batch', adminToken),
};
