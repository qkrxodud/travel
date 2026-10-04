/**
 * 첫 화면이 어디서 들어왔는지(app_open.entry) — 카드·프로필 유입은 K 계수의 근거다(계약 §1-1 "entry 정하는 법").
 *
 * 1. 주소의 표시 `?from=card|profile|invite|direct` 가 있으면 그것(공유 링크에 화면이 붙이는 표시).
 * 2. 공개 프로필의 "이 지도에 합류"(`?joinProfile=`)면 profile.
 * 3. 같은 출처의 공개 페이지에서 왔으면: 카드 이미지(`/u/{handle}/card/…`·`/vs/…`·`.png`) card, 공개 프로필(`/u/…`) profile.
 * 4. 다른 사이트에서 왔으면 other, 아무 표시도 없으면 direct.
 * 주소·이전 페이지의 handle 같은 값은 쓰지 않고 갈래 이름만 돌려준다.
 */
import type { EntryPoint } from '../../../api/types/analytics';

export const ENTRY_PARAM = 'from';
const MARKED: readonly EntryPoint[] = ['card', 'profile', 'invite', 'direct'];

export function entryPoint(href: string, referrer: string): EntryPoint {
  const here = safeUrl(href);
  const marked = here?.searchParams.get(ENTRY_PARAM);
  if (marked && (MARKED as readonly string[]).includes(marked)) return marked as EntryPoint;
  if (here?.searchParams.has('joinProfile')) return 'profile';
  const before = referrer ? safeUrl(referrer) : null;
  if (!before) return 'direct';
  if (here && before.origin === here.origin) {
    const path = before.pathname;
    if (!path.startsWith('/u/')) return 'direct';
    return path.endsWith('.png') || path.includes('/card/') || path.includes('/vs/') ? 'card' : 'profile';
  }
  return 'other';
}

/** 갈래 표시(`from`)를 뺀 주소 — 새로고침·즐겨찾기에 표시가 남지 않게. 바꿀 것이 없으면 null. */
export function withoutEntryMark(href: string): string | null {
  const here = safeUrl(href);
  if (!here || !here.searchParams.has(ENTRY_PARAM)) return null;
  here.searchParams.delete(ENTRY_PARAM);
  return here.pathname + here.search + here.hash;
}

function safeUrl(value: string): URL | null {
  try {
    return new URL(value);
  } catch {
    return null;
  }
}
