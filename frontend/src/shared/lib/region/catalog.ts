/**
 * 카탈로그 색인(서버 /catalog/* 응답 → 화면이 찾기 쉬운 모양). 지역 코드는 화면 코드(11010).
 * 값은 서버 카탈로그 그대로다(희귀도·XP·시·도 정복 수 등) — 화면은 계산하지 않고 찾기만 한다.
 */
import { toClientCode } from '../../../api/client';
import type { ItemView, ProvinceView, RegionFeatureCollection, RegionGeometry, RewardRulesView } from '../../../api/types/catalog';
import type { Rarity } from '../../../api/types/common';

/** 화면 희귀도(CSS 클래스·라벨 키) */
export type Tier = 'common' | 'rare' | 'legend';

export const TIER_LABEL: Readonly<Record<Tier, string>> = { common: '일반', rare: '희귀', legend: '전설' };

export function toTier(rarity: Rarity | null | undefined): Tier {
  return rarity === 'LEGEND' ? 'legend' : rarity === 'RARE' ? 'rare' : 'common';
}

export interface RegionProperties {
  /** 화면 지역 코드(11010) */
  code: string;
  name: string;
  /** 시·도 이름(서울) */
  prov: string;
  tier: Tier;
}

export interface RegionFeature {
  type: 'Feature';
  properties: RegionProperties;
  geometry: RegionGeometry;
}

export interface Catalog {
  features: RegionFeature[];
  byCode: ReadonlyMap<string, RegionFeature>;
  /** 시·도 이름(표시 순) */
  provinces: string[];
  provinceTotal: ReadonlyMap<string, number>;
  /** 시·도 코드(KR-11) → 이름 */
  provinceByCode: ReadonlyMap<string, string>;
  /** 화면 지역 코드 → 그 지역 아이템 정의 */
  itemByRegion: ReadonlyMap<string, ItemView>;
  itemById: ReadonlyMap<string, ItemView>;
  xpByTier: Readonly<Record<Tier, number>>;
  provinceFirstBonus: number;
  /** 시·도 정복 보너스 XP(서버 reward-rules) */
  provinceConquestBonus: number;
}

export function buildCatalog(geo: RegionFeatureCollection, provinces: ProvinceView[], items: ItemView[], rules: RewardRulesView): Catalog {
  const features: RegionFeature[] = geo.features.map(feature => ({
    type: 'Feature',
    properties: {
      code: toClientCode(feature.properties.code),
      name: feature.properties.name,
      prov: feature.properties.province,
      tier: toTier(feature.properties.rarity),
    },
    geometry: feature.geometry,
  }));
  const ordered = [...provinces].sort((left, right) => left.displayOrder - right.displayOrder);
  return {
    features,
    byCode: new Map(features.map(feature => [feature.properties.code, feature])),
    provinces: ordered.map(province => province.name),
    provinceTotal: new Map(ordered.map(province => [province.name, province.regionCount])),
    provinceByCode: new Map(ordered.map(province => [province.code, province.name])),
    itemByRegion: new Map(items.filter(item => item.regionCode).map(item => [toClientCode(item.regionCode as string), item])),
    itemById: new Map(items.map(item => [item.itemId, item])),
    xpByTier: { common: rules.xpByRarity.COMMON, rare: rules.xpByRarity.RARE, legend: rules.xpByRarity.LEGEND },
    provinceFirstBonus: rules.provinceFirstBonus,
    provinceConquestBonus: rules.provinceConquestBonus,
  };
}

/** "시·도 이름 지역 이름" (경기 가평군) */
export function regionLabel(feature: RegionFeature): string {
  return `${feature.properties.prov} ${feature.properties.name}`;
}

/** 서버·화면 지역 코드 → 라벨(모르는 코드는 그대로) */
export function labelOfCode(catalog: Catalog, code: string): string {
  const feature = catalog.byCode.get(toClientCode(code));
  return feature ? regionLabel(feature) : code;
}
