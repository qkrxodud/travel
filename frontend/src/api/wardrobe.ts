/** 꾸미기(3단계): 가방·장면 — 지급·자동 착용은 이벤트로 비동기 반영된다. 검증(보유·슬롯·장식 ≤3)은 서버가 한다. */
import { apiClient } from './client';
import type { InventoryResponse, SceneRequest, SceneResponse } from './types/wardrobe';

export const wardrobeApi = {
  inventory: () => apiClient.request<InventoryResponse>('GET', '/inventory'),
  scene: () => apiClient.request<SceneResponse>('GET', '/scene'),
  editScene: (body: SceneRequest) => apiClient.request<SceneResponse>('PUT', '/scene', body),
  favorite: (itemId: string, favorite: boolean) =>
    apiClient.request<unknown>('PUT', '/inventory/' + encodeURIComponent(itemId) + '/favorite', { favorite }),
};
