/**
 * 첫 화면이 어디서 들어왔는지(app_open.entry) — 카드·프로필 유입은 K 계수의 근거다(계약 §1-1 "entry 정하는 법").
 *
 * 1. 주소의 표시 `?from=card|profile|invite|direct|push` 가 있으면 그것(공유 링크·알림 주소에 붙는 표시 — push 는 12단계 알림 클릭).
 * 2. 공개 프로필의 "이 지도에 합류"(`?joinProfile=`)면 profile.
 * 3. 같은 출처의 공개 페이지에서 왔으면: 카드 이미지(`/u/{handle}/card/…`·`/vs/…`·`.png`) card, 공개 프로필(`/u/…`) profile.
 * 4. 다른 사이트에서 왔으면 other, 아무 표시도 없으면 direct.
 * 주소·이전 페이지의 handle 같은 값은 쓰지 않고 갈래 이름만 돌려준다.
 */
import type { EntryPoint, PushKind } from '../../../api/types/analytics';

export const ENTRY_PARAM = 'from';
/** 알림 주소(`/?from=push&push=mystery#map`)의 알림 종류 표시 */
export const PUSH_KIND_PARAM = 'push';
const MARKED: readonly EntryPoint[] = ['card', 'profile', 'invite', 'direct', 'push'];
const PUSH_KINDS: readonly PushKind[] = ['mystery', 'streak', 'season'];

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

/** 알림을 눌러 열었으면 그 알림 종류(`?from=push&push=종류`), 아니면 null — 모르는 종류도 null. */
export function pushOpenKind(href: string): PushKind | null {
  const here = safeUrl(href);
  if (here?.searchParams.get(ENTRY_PARAM) !== 'push') return null;
  const kind = here.searchParams.get(PUSH_KIND_PARAM);
  return kind && (PUSH_KINDS as readonly string[]).includes(kind) ? (kind as PushKind) : null;
}

/** 갈래 표시(`from`, 알림 종류 `push`)를 뺀 주소 — 새로고침·즐겨찾기에 표시가 남지 않게. 바꿀 것이 없으면 null. */
export function withoutEntryMark(href: string): string | null {
  const here = safeUrl(href);
  if (!here || (!here.searchParams.has(ENTRY_PARAM) && !here.searchParams.has(PUSH_KIND_PARAM))) return null;
  here.searchParams.delete(ENTRY_PARAM);
  here.searchParams.delete(PUSH_KIND_PARAM);
  return here.pathname + here.search + here.hash;
}

function safeUrl(value: string): URL | null {
  try {
    return new URL(value);
  } catch {
    return null;
  }
}
