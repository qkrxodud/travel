/**
 * 12단계 웹 푸시 알림(계약 _workspace/12_contracts.md §1·§2) — 백엔드 notification.api.web.PushDtos 와 1:1.
 */
import type { PushKind } from './analytics';

export type { PushKind };

/** GET /push/vapid-public-key — base64url(채움 없음) P-256 공개 키 */
export interface VapidKeyResponse {
  publicKey: string;
}

/** POST /push/subscriptions 본문 — 브라우저 PushSubscription.toJSON() 그대로 */
export interface SubscriptionRequest {
  endpoint: string;
  expirationTime: number | null;
  keys: { p256dh: string; auth: string };
}

/** POST /push/subscriptions 응답 */
export interface SubscriptionResponse {
  /** 새 기기면 true(같은 주소의 갱신이면 false) */
  created: boolean;
  /** 지금 알림 받는 기기 수 */
  devices: number;
  /** 기기 수 상한 때문에 뺀 가장 오래된 기기 수 */
  evicted: number;
}

/** DELETE /push/subscriptions 본문 */
export interface UnsubscribeRequest {
  endpoint: string;
}

/** 종류별 알림 켜고 끄기 — PUT 은 세 값 모두 필수 */
export interface PreferencesRequest {
  mystery: boolean;
  streak: boolean;
  season: boolean;
}

export interface QuietHoursView {
  /** HH:mm(서비스 시간대) */
  start: string;
  end: string;
  timeZone: string;
}

/** GET·PUT /push/preferences 응답 */
export interface PreferencesResponse extends PreferencesRequest {
  /** 알림 받는 기기 수(0 이면 아무 알림도 가지 않는다) */
  devices: number;
  quietHours: QuietHoursView;
  /** 하루에 받는 알림 최대 개수 */
  dailyLimit: number;
}

/** 서비스워커가 받는 푸시 본문(event.data.json()) */
export interface PushPayload {
  kind: PushKind;
  title: string;
  body: string;
  /** 같은 출처 경로(`/?from=push&push=mystery#map`) */
  url: string;
  tag: string;
}
