import type { Rarity } from './common';

export type ProfileVisibility = 'PUBLIC' | 'FRIENDS' | 'PRIVATE';
export type CardKind = 'territory' | 'recent' | 'recap';

/** SharingDtos.CardMetaResponse (kind 는 TERRITORY·RECENT·RECAP) */
export interface CardMetaResponse {
  kind: string;
  previewUrl: string;
  publicUrl: string | null;
  rendered: boolean;
  stale: boolean;
  renderedAt: string | null;
}

/** GET /me/cards — MyCardsResponse */
export interface MyCardsResponse {
  handle: string | null;
  profileUrl: string | null;
  visibility: ProfileVisibility;
  publiclyVisible: boolean;
  cards: CardMetaResponse[];
}

/** PUT /me/privacy — PrivacyResponse */
export interface PrivacyResponse {
  visibility: ProfileVisibility;
  publiclyVisible: boolean;
  updatedAt: string | null;
}

/** SharingDtos.RecapProvinceResponse — 가장 많이 간 시·도(provinceName 은 짧은 이름 "서울") */
export interface RecapProvinceResponse {
  provinceCode: string;
  provinceName: string;
  count: number;
}

/** SharingDtos.RecapRegionResponse — 가장 희귀한 곳 */
export interface RecapRegionResponse {
  regionCode: string;
  name: string;
  provinceCode: string;
  provinceName: string;
  rarity: Rarity;
}

/** SharingDtos.RecapMonthResponse — 가장 바쁜 달(1~12) */
export interface RecapMonthResponse {
  month: number;
  count: number;
}

/**
 * GET /me/recap?year=&mapId= — RecapResponse. 고른 지도에서 내가 체크인한 방문만 센 연간 리캡(카드 PNG 와 같은 계산).
 * 동점 규칙은 서버 몫: 시·도·희귀한 곳은 지역 코드가 작은 쪽, 달은 이른 달. setsCompleted 는 연도와 무관한 누적값.
 */
export interface RecapResponse {
  year: number;
  mapId: string;
  newRegions: number;
  /** 항상 12칸, index 0 = 1월 */
  monthCounts: number[];
  topProvince: RecapProvinceResponse | null;
  rarest: RecapRegionResponse | null;
  newProvinces: number;
  busiestMonth: RecapMonthResponse | null;
  setsCompleted: number;
}
