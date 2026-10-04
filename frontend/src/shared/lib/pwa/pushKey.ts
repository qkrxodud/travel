/** 웹 푸시 키 변환(순수) — 서버 공개 키(base64url, 채움 없음) → applicationServerKey 바이트, 브라우저 구독 → 서버 요청 본문. */
import type { SubscriptionRequest } from '../../../api/types/notification';

export function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

/** PushSubscription.toJSON() → POST /push/subscriptions 본문(키가 빠졌으면 null) */
export function subscriptionRequest(json: PushSubscriptionJSON): SubscriptionRequest | null {
  const p256dh = json.keys?.p256dh;
  const auth = json.keys?.auth;
  if (!json.endpoint || !p256dh || !auth) return null;
  return { endpoint: json.endpoint, expirationTime: json.expirationTime ?? null, keys: { p256dh, auth } };
}
