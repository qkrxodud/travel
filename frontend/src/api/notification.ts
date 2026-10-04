/** 웹 푸시 알림(12단계): 공개 키 · 이 브라우저 구독/해지 · 종류별 설정 */
import { apiClient } from './client';
import type { PreferencesRequest, PreferencesResponse, SubscriptionRequest, SubscriptionResponse, VapidKeyResponse } from './types/notification';

export const notificationApi = {
  /** 인증 불필요 — 탐험가 발급을 일으키지 않는다 */
  vapidPublicKey: () => apiClient.publicJson<VapidKeyResponse>('/push/vapid-public-key'),
  subscribe: (subscription: SubscriptionRequest) => apiClient.request<SubscriptionResponse>('POST', '/push/subscriptions', subscription),
  unsubscribe: (endpoint: string) => apiClient.request<null>('DELETE', '/push/subscriptions', { endpoint }),
  preferences: () => apiClient.request<PreferencesResponse>('GET', '/push/preferences'),
  savePreferences: (preferences: PreferencesRequest) => apiClient.request<PreferencesResponse>('PUT', '/push/preferences', preferences),
};
