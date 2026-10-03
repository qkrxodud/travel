/**
 * 가방 탭 표시 로직(순수 함수). 보유·착용·꾸미기 점수는 서버 값 — 여기서는 거르기·정렬·문구만.
 */
import { catalogItem, itemOrigin, type DisplayItem } from '../../../shared/lib/item/displayItem';
import type { MyVisit } from '../../../shared/lib/territory/visits';
import type { Catalog, Tier } from '../../../shared/lib/region/catalog';

export type BagFilterKey = 'all' | 'hat' | 'hand' | 'badge' | 'back' | 'pet' | 'bg' | 'prop';

export const BAG_FILTERS: readonly (readonly [BagFilterKey, string])[] = [
  ['all', '전체'], ['hat', '모자'], ['hand', '손'], ['badge', '키링'], ['back', '배낭'], ['pet', '동행'], ['bg', '배경·풍경'], ['prop', '장식'],
];

const TIER_ORDER: Readonly<Record<Tier, number>> = { legend: 0, rare: 1, common: 2 };

/** 필터에 맞는 아이템 — 즐겨찾기 먼저, 그다음 희귀도(전설→희귀→일반). 같으면 서버 순서. */
export function filterAndSort(items: readonly DisplayItem[], filter: BagFilterKey): DisplayItem[] {
  return items
    .filter(item => filter === 'all' || item.slot === filter)
    .map((item, index) => ({ item, index }))
    .sort((left, right) => (Number(right.item.favorite) - Number(left.item.favorite))
      || (TIER_ORDER[left.item.tier] - TIER_ORDER[right.item.tier]) || left.index - right.index)
    .map(entry => entry.item);
}

/** 꾸미기 점수(서버 계산) → 등급 문구 */
export function styleRank(points: number): string {
  return points >= 30 ? '전설급 꾸미기' : points >= 18 ? '화려함' : points >= 8 ? '제법 꾸밈' : points > 0 ? '소박함' : '맨몸';
}

export const isSetReward = (item: Pick<DisplayItem, 'code'>): boolean => item.code.startsWith('set:');

const WANT_COUNT = 4;

/** 갖고 싶은 것: 아직 안 간 곳의 아이템: 전설 지역 4 + 배경·장식 4 (카탈로그 순) */
export function wantedItems(catalog: Catalog, visited: ReadonlyMap<string, MyVisit> | null): { code: string; item: DisplayItem }[] {
  const unvisited = catalog.features.filter(feature => !visited?.has(feature.properties.code));
  const toItem = (code: string) => {
    const view = catalog.itemByRegion.get(code);
    return view ? { code, item: catalogItem(view, itemOrigin(view.itemId, catalog, () => undefined)) } : null;
  };
  const present = <T,>(entry: T | null): entry is T => entry !== null;
  const legends = unvisited.filter(feature => feature.properties.tier === 'legend').slice(0, WANT_COUNT).map(feature => toItem(feature.properties.code)).filter(present);
  const scenery = unvisited.map(feature => toItem(feature.properties.code)).filter(present)
    .filter(entry => entry.item.slot === 'bg' || entry.item.slot === 'prop').slice(0, WANT_COUNT);
  return [...legends, ...scenery];
}

