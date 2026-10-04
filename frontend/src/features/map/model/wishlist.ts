/**
 * 가고 싶은 곳(9단계) 표시 로직(순수 함수). 핀·다녀옴·상한·XP 는 서버 값(GET /wishlist), 칠했는지는 탐험가 단위 서버 판정(GET /revisits/{code}.painted).
 */
import type { ErrorCode } from '../../../api/types/common';
import type { WishItem, WishlistResponse } from '../../../api/types/exploration';

export type WishState = 'pinned' | 'visited' | 'painted' | 'full' | 'open';

export interface WishToggleView {
  state: WishState;
  pressed: boolean;
  disabled: boolean;
  label: string;
  /** 꽂을 수 없는 이유나 꽂은 뒤 안내 */
  hint: string | null;
}

const fullText = (max: number) => `가고 싶은 곳은 ${max}곳까지 꽂을 수 있어요 — 다녀오거나 빼면 다시 꽂을 수 있어요`;
const PAINTED_TEXT = '이미 칠한 곳이에요 — 다시 가면 "다시 다녀왔어요"로 도장을 받을 수 있어요';

/**
 * 지역 상세 "가고 싶어요" 토글. serverCode = KR-xxxxx, painted = 탐험가 단위로(어느 지도든) 칠했는지.
 * 순서: 다녀옴 → 꽂음(뺄 수 있다) → 이미 칠함(비활성) → 상한(비활성) → 꽂을 수 있음.
 */
export function wishToggle(serverCode: string, wishlist: WishlistResponse, painted: boolean): WishToggleView {
  const pin = wishlist.items.find(item => item.regionCode === serverCode);
  if (pin?.status === 'VISITED') return { state: 'visited', pressed: true, disabled: true, label: '✓ 가고 싶던 곳 — 다녀왔어요', hint: null };
  if (pin) return { state: 'pinned', pressed: true, disabled: false, label: '★ 가고 싶은 곳', hint: `칠하면 다녀옴 +${wishlist.xpPerWish} XP · 다시 누르면 빼요` };
  if (painted) return { state: 'painted', pressed: false, disabled: true, label: '☆ 가고 싶어요', hint: PAINTED_TEXT };
  if (wishlist.pendingCount >= wishlist.max) return { state: 'full', pressed: false, disabled: true, label: '☆ 가고 싶어요', hint: fullText(wishlist.max) };
  return { state: 'open', pressed: false, disabled: false, label: '☆ 가고 싶어요', hint: null };
}

export interface WishSections {
  /** "3 / 30" — 아직 다녀오지 않은 핀 / 상한 */
  summary: string;
  pending: WishItem[];
  visited: WishItem[];
}

/** 사이드 "가고 싶은 곳" 목록(남은 곳·다녀온 곳, 서버 순서 = 최근에 꽂은 순) */
export function wishSections(wishlist: WishlistResponse): WishSections {
  return {
    summary: `${wishlist.pendingCount} / ${wishlist.max}`,
    pending: wishlist.items.filter(item => item.status === 'WANTED'),
    visited: wishlist.items.filter(item => item.status === 'VISITED'),
  };
}

/** 아직 다녀오지 않은 핀 지역(지도 핀 레이어, 서버 코드 그대로) */
export function pendingWishCodes(wishlist: WishlistResponse | undefined): string[] {
  return (wishlist?.items ?? []).filter(item => item.status === 'WANTED').map(item => item.regionCode);
}

/** 꽂지 못했을 때 오류 코드별 안내 */
export function pinErrorText(code: ErrorCode | undefined, max: number | undefined): string | null {
  if (code === 'WISH_ALREADY_VISITED') return PAINTED_TEXT;
  if (code === 'WISHLIST_FULL') return max ? fullText(max) : '가고 싶은 곳이 가득 찼어요 — 다녀오거나 빼면 다시 꽂을 수 있어요';
  return null;
}
