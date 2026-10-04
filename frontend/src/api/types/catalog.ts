import type { Rarity } from './common';

/** catalog ItemView.Look — 픽셀 페인터 룩(타입·주색·보조색) */
export interface ItemLook {
  type: string;
  primary: string;
  secondary: string;
}

/** 서버 장착 슬롯(EquipSlot). 화면은 BAG 을 "배낭"(back) 으로 그린다. */
export type ServerSlot = 'HAT' | 'HAND' | 'BADGE' | 'BAG' | 'PET' | 'BG' | 'PROP';

/** GET /catalog/items 한 줄 — catalog ItemView */
export interface ItemView {
  itemId: string;
  regionCode: string | null;
  name: string;
  emoji: string;
  slot: ServerSlot;
  tier: Rarity;
  theme: string | null;
  look: ItemLook | null;
  grantRule: string;
  grantRef: string | null;
  validFrom: string | null;
  validTo: string | null;
  /** 재방문 2회차 색 변형 룩(지역 특산물만, 그 외 null — 9단계) */
  variantLook: ItemLook | null;
}

/** GET /catalog/provinces 한 줄 — catalog ProvinceView */
export interface ProvinceView {
  code: string;
  name: string;
  fullName: string;
  displayOrder: number;
  regionCount: number;
}

/** GET /catalog/reward-rules — catalog RewardRulesView */
export interface RewardRulesView {
  xpByRarity: Record<Rarity, number>;
  provinceFirstBonus: number;
  setCompleteBonus: number;
  claimBonus: number;
  /** 이번 주 미스터리 보너스(8단계) */
  mysteryBonus: number;
  /** 시·도 정복 보너스(8단계) */
  provinceConquestBonus: number;
  /** 계절 한정 테마 완성 보너스(9단계) */
  seasonCompleteBonus: number;
  /** 재방문 도장 보너스(9단계) */
  revisitStampBonus: number;
  /** 가고 싶은 곳 다녀옴 보너스(9단계) */
  wishFulfilledBonus: number;
}

/** GET /catalog/regions.geojson 의 Feature.properties (서버 코드 KR-xxxxx) */
export interface RegionFeatureProperties {
  code: string;
  name: string;
  provinceCode: string;
  province: string;
  rarity: Rarity;
}

export interface RegionGeometry {
  type: 'Polygon' | 'MultiPolygon';
  coordinates: number[][][] | number[][][][];
}

export interface RegionFeatureCollection {
  type: 'FeatureCollection';
  features: { type: 'Feature'; properties: RegionFeatureProperties; geometry: RegionGeometry }[];
}
