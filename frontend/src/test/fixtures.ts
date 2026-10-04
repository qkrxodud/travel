/** 단위 테스트 공용 가짜 데이터 */
import type { ItemView, ProvinceView, RegionFeatureCollection, RewardRulesView } from '../api/types/catalog';
import type { TerritoryResponse, VisitResponse } from '../api/types/exploration';
import { buildCatalog } from '../shared/lib/region/catalog';

const square = (left: number, top: number) => ({
  type: 'Polygon' as const,
  coordinates: [[[left, top], [left + 0.2, top], [left + 0.2, top - 0.2], [left, top - 0.2], [left, top]]],
});

export const GEO: RegionFeatureCollection = {
  type: 'FeatureCollection',
  features: [
    { type: 'Feature', properties: { code: 'KR-11010', name: '종로구', provinceCode: 'KR-11', province: '서울', rarity: 'COMMON' }, geometry: square(126.9, 37.6) },
    { type: 'Feature', properties: { code: 'KR-11020', name: '중구', provinceCode: 'KR-11', province: '서울', rarity: 'COMMON' }, geometry: square(127.1, 37.6) },
    { type: 'Feature', properties: { code: 'KR-31370', name: '가평군', provinceCode: 'KR-31', province: '경기', rarity: 'RARE' }, geometry: square(127.4, 37.8) },
    { type: 'Feature', properties: { code: 'KR-37430', name: '울릉군', provinceCode: 'KR-37', province: '경북', rarity: 'LEGEND' }, geometry: square(130.8, 37.5) },
  ],
};

export const PROVINCES: ProvinceView[] = [
  { code: 'KR-31', name: '경기', fullName: '경기도', displayOrder: 2, regionCount: 1 },
  { code: 'KR-11', name: '서울', fullName: '서울특별시', displayOrder: 1, regionCount: 2 },
  { code: 'KR-37', name: '경북', fullName: '경상북도', displayOrder: 3, regionCount: 1 },
];

const item = (itemId: string, regionCode: string | null, name: string, emoji: string, slot: ItemView['slot'], tier: ItemView['tier'], look: ItemView['look'], theme: string | null = null): ItemView =>
  ({ itemId, regionCode, name, emoji, slot, tier, theme, look, grantRule: 'REGION_VISIT', grantRef: regionCode, validFrom: null, validTo: null, variantLook: look ? { type: look.type, primary: look.secondary, secondary: look.primary } : null });

export const ITEMS: ItemView[] = [
  item('region:KR-11010', 'KR-11010', '청사초롱 등불', '🏮', 'HAND', 'COMMON', { type: 'lantern', primary: '#e63946', secondary: '#f4c542' }),
  item('region:KR-11020', 'KR-11020', '명동 칼국수 키링', '🍜', 'BADGE', 'COMMON', { type: 'keyring', primary: '#f4c542', secondary: '#f7f3ea' }),
  item('region:KR-31370', 'KR-31370', '가평 잣 다람쥐', '🐿️', 'PET', 'RARE', { type: 'rodent', primary: '#8d5524', secondary: '#f3e9dc' }),
  item('region:KR-37430', 'KR-37430', '울릉 독도 바다 풍경', '🌊', 'BG', 'LEGEND', null, 'dokdo'),
];

export const RULES: RewardRulesView = { xpByRarity: { COMMON: 10, RARE: 20, LEGEND: 50 }, provinceFirstBonus: 15, setCompleteBonus: 100, claimBonus: 10, mysteryBonus: 50, provinceConquestBonus: 300, seasonCompleteBonus: 150, revisitStampBonus: 10, wishFulfilledBonus: 20 };

export const CATALOG = buildCatalog(GEO, PROVINCES, ITEMS, RULES);

export function visit(code: string, overrides: Partial<VisitResponse> = {}): VisitResponse {
  return {
    regionCode: 'KR-' + code, regionName: '', provinceCode: '', provinceName: '', rarity: 'COMMON', visitDate: '2026-10-01', memo: '',
    photoUrl: null, checkedInBy: 'me', verification: 'NONE', visitedAt: '2026-10-01T03:00:00Z', claim: true, disputed: false, generation: 1,
    ...overrides,
  };
}

export function territory(overrides: Partial<TerritoryResponse> = {}): TerritoryResponse {
  return {
    mapId: 'personal', mapName: '나의 영토', mapKind: 'PERSONAL', conquest: { visited: 0, total: 4, percent: 0 },
    provinces: [], visits: [], claims: [], ...overrides,
  };
}
