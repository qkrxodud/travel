/**
 * 서비스워커 규칙(순수 TS — 서비스워커와 단위 테스트가 함께 쓴다). 앱 화면 코드는 이 파일을 import 하지 않는다
 * (서비스워커 번들 sw.js 가 앱 청크를 끌고 오지 않게).
 *
 * - 캐시: 빌드 산출물 중 이름에 해시가 붙은 정적 자산(/assets/*)과 아이콘·매니페스트만. 화면(index.html)·API·관리자·개발 경로는
 *   캐시하지 않는다 — 화면은 늘 서버에서 받아 새 배포의 자산 이름을 바로 따른다.
 * - 알림: 푸시 본문({kind,title,body,url,tag}) → showNotification 옵션. 클릭 주소는 같은 출처 경로만(다른 출처면 첫 화면).
 */
import type { PushKind, PushPayload } from '../api/types/notification';

export const CACHE_PREFIX = 'territory-static-';
export const MESSAGE_SKIP_WAITING = 'SKIP_WAITING';
export const MESSAGE_SUBSCRIPTION_CHANGED = 'PUSH_SUBSCRIPTION_CHANGED';
export const ICON_PATH = '/icons/icon-192.png';
export const BADGE_PATH = '/icons/badge-96.png';

const CACHEABLE_PREFIXES = ['/assets/', '/icons/'];
const CACHEABLE_FILES = ['/manifest.json'];
const PUSH_KINDS: readonly PushKind[] = ['mystery', 'streak', 'season'];
const FALLBACK_TITLE = '나의 영토';

export function cacheName(build: string): string {
  return CACHE_PREFIX + build;
}

/** 지난 빌드의 캐시(지울 것) — 우리 접두사가 붙은 것 중 지금 빌드가 아닌 것만 */
export function staleCaches(names: readonly string[], build: string): string[] {
  return names.filter(name => name.startsWith(CACHE_PREFIX) && name !== cacheName(build));
}

/** 캐시에서 내줄 요청인지 — 같은 출처 GET 의 정적 자산만(화면·API·관리자·개발 경로는 늘 네트워크) */
export function isCacheable(method: string, url: string, origin: string): boolean {
  if (method !== 'GET') return false;
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return false;
  }
  if (parsed.origin !== origin || parsed.search) return false;
  const path = parsed.pathname;
  return CACHEABLE_PREFIXES.some(prefix => path.startsWith(prefix)) || CACHEABLE_FILES.includes(path);
}

/** 미리 받아 둘 자산 — 빌드 목록 중 캐시 규칙에 맞는 것만 */
export function precacheList(files: readonly string[], origin: string): string[] {
  return files.map(file => (file.startsWith('/') ? file : '/' + file)).filter(path => isCacheable('GET', origin + path, origin));
}

/** 같은 출처 경로만 연다(다른 출처·형식이 틀린 주소는 첫 화면) */
export function clickTarget(url: unknown, origin: string): string {
  if (typeof url !== 'string' || !url) return origin + '/';
  try {
    const target = new URL(url, origin);
    return target.origin === origin ? target.href : origin + '/';
  } catch {
    return origin + '/';
  }
}

export interface NotificationSpec {
  title: string;
  options: { body: string; tag?: string; icon: string; badge: string; data: { url: string; kind: PushKind | null } };
}

/** 푸시 본문 → 알림. 본문이 깨져 있어도 알림은 띄운다(userVisibleOnly 약속 — 받은 푸시는 반드시 보인다). */
export function notificationFrom(payload: unknown, origin: string): NotificationSpec {
  const record: Partial<Record<keyof PushPayload, unknown>> = payload && typeof payload === 'object' ? (payload as Record<string, unknown>) : {};
  const kind = typeof record.kind === 'string' && (PUSH_KINDS as readonly string[]).includes(record.kind) ? (record.kind as PushKind) : null;
  const title = typeof record.title === 'string' && record.title ? record.title : FALLBACK_TITLE;
  const body = typeof record.body === 'string' ? record.body : '';
  const tag = typeof record.tag === 'string' && record.tag ? record.tag : undefined;
  return {
    title,
    options: { body, ...(tag ? { tag } : {}), icon: ICON_PATH, badge: BADGE_PATH, data: { url: clickTarget(record.url, origin), kind } },
  };
}

/** 푸시 본문 문자열 → JSON(깨졌으면 null) */
export function parsePayload(text: string | null): unknown {
  if (!text) return null;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return null;
  }
}

export interface WindowLike {
  url: string;
  focused: boolean;
  visibilityState: string;
}

/** 알림을 눌렀을 때 쓸 창 — 같은 출처 창 중 보고 있던 창 → 보이는 창 → 아무 창. 없으면 -1(새 창). */
export function pickWindow(windows: readonly WindowLike[], origin: string): number {
  const sameOrigin = windows.map((window, index) => ({ window, index })).filter(({ window }) => {
    try {
      return new URL(window.url).origin === origin;
    } catch {
      return false;
    }
  });
  const best = sameOrigin.find(({ window }) => window.focused)
    ?? sameOrigin.find(({ window }) => window.visibilityState === 'visible')
    ?? sameOrigin[0];
  return best ? best.index : -1;
}
