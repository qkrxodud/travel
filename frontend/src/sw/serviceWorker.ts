/**
 * 나의 영토 서비스워커(12단계). 빌드가 sw.js 로 낸다(vite.config.ts 의 territoryServiceWorker — 빌드 번호·미리 받을 자산 목록을 끼워 넣는다).
 *
 * - 설치: 이번 빌드의 정적 자산을 미리 받는다. 처음 설치면 바로 활성화, 이미 다른 버전이 이 화면을 맡고 있으면 기다린다 — 화면이
 *   "새 버전이 있어요 — 새로고침"을 띄우고, 누르면 SKIP_WAITING 메시지로 넘어간다.
 * - 활성화: 지난 빌드 캐시를 지운다.
 * - fetch: 정적 자산만 캐시 먼저(없으면 받아서 넣는다). 화면·API·관리자 경로는 손대지 않는다(respondWith 를 부르지 않음).
 * - push: 본문 → showNotification. notificationclick: 열린 창을 앞으로 + 알림 주소로, 없으면 새 창.
 * - pushsubscriptionchange: 같은 공개 키로 다시 구독하고 열린 화면에 알린다(화면이 서버에 보낸다 — 익명 토큰은 화면만 안다).
 */
import {
  cacheName, clickTarget, isCacheable, MESSAGE_SKIP_WAITING, MESSAGE_SUBSCRIPTION_CHANGED, notificationFrom, parsePayload, pickWindow,
  precacheList, staleCaches,
} from './swRules';

declare const self: ServiceWorkerGlobalScope;
/** 빌드가 끼워 넣는 값(vite.config.ts) */
declare const __TERRITORY_SW_BUILD__: string;
declare const __TERRITORY_SW_PRECACHE__: string[];

const BUILD = __TERRITORY_SW_BUILD__;
const CACHE = cacheName(BUILD);
const ORIGIN = self.location.origin;

interface SubscriptionChangeEvent extends ExtendableEvent {
  readonly oldSubscription: PushSubscription | null;
  readonly newSubscription: PushSubscription | null;
}

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    // 자산 하나가 없어도 설치는 끝낸다(다음 요청 때 받아 넣는다)
    await Promise.all(precacheList(__TERRITORY_SW_PRECACHE__, ORIGIN).map(path => cache.add(path).catch(() => undefined)));
    if (!self.registration.active) await self.skipWaiting();
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(staleCaches(names, BUILD).map(name => caches.delete(name)));
    await self.clients.claim();
  })());
});

self.addEventListener('message', event => {
  const data: unknown = event.data;
  if (data && typeof data === 'object' && (data as { type?: unknown }).type === MESSAGE_SKIP_WAITING) void self.skipWaiting();
});

self.addEventListener('fetch', event => {
  const { request } = event;
  if (!isCacheable(request.method, request.url, ORIGIN)) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE);
    const hit = await cache.match(request);
    if (hit) return hit;
    const response = await fetch(request);
    if (response.ok && response.type === 'basic') await cache.put(request, response.clone());
    return response;
  })());
});

function pushText(data: PushMessageData | null): string | null {
  try {
    return data ? data.text() : null;
  } catch {
    return null;
  }
}

self.addEventListener('push', event => {
  const spec = notificationFrom(parsePayload(pushText(event.data)), ORIGIN);
  event.waitUntil(self.registration.showNotification(spec.title, spec.options));
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  const data: unknown = event.notification.data;
  const target = clickTarget(data && typeof data === 'object' ? (data as { url?: unknown }).url : null, ORIGIN);
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    const index = pickWindow(windows.map(client => ({ url: client.url, focused: client.focused, visibilityState: client.visibilityState })), ORIGIN);
    const chosen = index >= 0 ? windows[index] : undefined;
    if (chosen) {
      try {
        const focused = await chosen.focus();
        // 화면이 알림 주소(?from=push&push=종류)로 새로 열려 app_open(entry=push)·push_open 을 보낸다
        await focused.navigate(target);
        return;
      } catch {
        // 이 서비스워커가 맡지 않은 창은 navigate 할 수 없다 — 새 창으로
      }
    }
    await self.clients.openWindow(target);
  })());
});

self.addEventListener('pushsubscriptionchange', rawEvent => {
  const event = rawEvent as SubscriptionChangeEvent;
  event.waitUntil((async () => {
    const key = event.oldSubscription?.options.applicationServerKey ?? null;
    if (!event.newSubscription && key) {
      // 실패하면 다음에 화면을 열 때 다시 구독하지 않는다(사용자가 프로필 탭에서 켠다) — 서버는 옛 주소가 410 이면 지운다
      await self.registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key }).catch(() => null);
    }
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const client of windows) client.postMessage({ type: MESSAGE_SUBSCRIPTION_CHANGED });
  })());
});
