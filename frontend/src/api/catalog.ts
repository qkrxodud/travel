/** 카탈로그(정적 참조 데이터) — 탐험가 식별 없이 열려 있다. GeoJSON·아이템 정의는 번들에 넣지 않고 서버에서 받는다. */
import { apiClient } from './client';
import type { ItemView, ProvinceView, RegionFeatureCollection, RewardRulesView } from './types/catalog';

export const catalogApi = {
  regionsGeoJson: () => apiClient.publicJson<RegionFeatureCollection>('/catalog/regions.geojson'),
  provinces: () => apiClient.publicJson<ProvinceView[]>('/catalog/provinces'),
  items: () => apiClient.publicJson<ItemView[]>('/catalog/items'),
  rewardRules: () => apiClient.publicJson<RewardRulesView>('/catalog/reward-rules'),
};
