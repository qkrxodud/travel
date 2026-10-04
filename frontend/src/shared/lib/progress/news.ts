/**
 * 진행 값이 바뀔 때 새로 생긴 게임 소식(보호권 사용 · 연속 탐험 마일스톤 · 시·도 정복)을 고른다 — 서버가 판정한 값의 변화만 본다.
 * 알림(토스트)과 정복 순간 지도 하이라이트가 쓴다.
 */
import type { WishItem, WishlistResponse } from '../../../api/types/exploration';
import type { FreezeUseResponse, MilestoneResponse, ProgressResponse, ProvinceProgressResponse } from '../../../api/types/progression';
import type { InventoryResponse, OwnedItemResponse } from '../../../api/types/wardrobe';

export interface GameNews {
  /** 이번에 새로 쓴 보호권 기록(없으면 null) */
  freezeUsed: FreezeUseResponse | null;
  milestones: MilestoneResponse[];
  conquered: ProvinceProgressResponse[];
}

type GameProgress = Pick<ProgressResponse, 'streakFreeze' | 'milestones' | 'provinces'>;

export function gameNews(before: GameProgress, after: GameProgress): GameNews {
  const lastUsed = after.streakFreeze.lastUsed;
  const freezeUsed = lastUsed && lastUsed.at !== before.streakFreeze.lastUsed?.at ? lastUsed : null;
  const reachedBefore = new Set(before.milestones.filter(milestone => milestone.reached).map(milestone => milestone.months));
  const conqueredBefore = new Set(before.provinces.filter(province => province.conquered).map(province => province.code));
  return {
    freezeUsed,
    milestones: after.milestones.filter(milestone => milestone.reached && !reachedBefore.has(milestone.months)),
    conquered: after.provinces.filter(province => province.conquered && !conqueredBefore.has(province.code)),
  };
}

/** 정복 기록이 있는 시·도 코드(KR-11) — 지도 테두리·왕관 */
export function conqueredProvinceCodes(provinces: readonly ProvinceProgressResponse[] | undefined): ReadonlySet<string> {
  return new Set((provinces ?? []).filter(province => province.conquered).map(province => province.code));
}

/** 핀 지역 이름(카탈로그에서 못 찾으면 서버가 null — 화면 코드로 대신 부른다) */
export function wishRegionName(item: Pick<WishItem, 'regionCode' | 'regionName'>): string {
  return item.regionName ?? item.regionCode.replace(/^KR-/, '');
}

/** 가고 싶은 곳 중 이번에 새로 다녀온 곳(축하 알림) — 전에는 다녀오지 않았던 핀이 서버에서 다녀옴(VISITED)이 된 것 */
export function fulfilledWishes(before: WishlistResponse, after: WishlistResponse): WishItem[] {
  const visitedBefore = new Set(before.items.filter(item => item.status === 'VISITED').map(item => item.regionCode));
  const pinnedBefore = new Set(before.items.map(item => item.regionCode));
  return after.items.filter(item => item.status === 'VISITED' && pinnedBefore.has(item.regionCode) && !visitedBefore.has(item.regionCode));
}

/** 이번에 재방문 2회차 색으로 바뀐 보유 아이템(알림) */
export function newlyVariantItems(before: InventoryResponse, after: InventoryResponse): OwnedItemResponse[] {
  const variantBefore = new Map(before.items.map(owned => [owned.itemId, owned.variant]));
  return after.items.filter(owned => owned.variant > 1 && (variantBefore.get(owned.itemId) ?? 1) <= 1 && variantBefore.has(owned.itemId));
}
