/** local 프로파일 전용 개발 엔드포인트(예시 다시 채우기·전부 지우기). */
import { apiClient } from './client';
import type { ClearResponse, SeedResponse } from './types/dev';

export const devApi = {
  seed: () => apiClient.request<SeedResponse>('POST', '/dev/seed'),
  clearVisits: () => apiClient.request<ClearResponse>('DELETE', '/dev/visits'),
};
