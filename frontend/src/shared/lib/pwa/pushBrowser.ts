/**
 * 브라우저 쪽 웹 푸시(권한·구독) — 서버 호출은 하지 않는다(그건 api/notification). 흐름을 묶는 것은 features/pwa/queries.
 */
import { base64UrlToBytes, subscriptionRequest } from './pushKey';
import { pushSupport, readDevice, type PushSupport } from './device';
import { serviceWorkerClient } from './serviceWorkerClient';
import type { SubscriptionRequest } from '../../../api/types/notification';

export type PermissionState = NotificationPermission | 'unsupported';

export interface PushBrowser {
  support(): PushSupport;
  permission(): PermissionState;
  requestPermission(): Promise<PermissionState>;
  /** 이 브라우저의 지금 구독(없으면 null) */
  current(): Promise<SubscriptionRequest | null>;
  /** 서버 공개 키로 구독(이미 있으면 그것) */
  subscribe(publicKey: string): Promise<SubscriptionRequest>;
  /** 이 브라우저 구독 해지 — 해지한 구독 주소(없었으면 null) */
  unsubscribe(): Promise<string | null>;
}

async function pushManager(): Promise<PushManager | null> {
  const registration = await serviceWorkerClient.ready();
  return registration?.pushManager ?? null;
}

export const pushBrowser: PushBrowser = {
  support: () => pushSupport(readDevice(serviceWorkerClient.enabled)),
  permission: () => (typeof Notification === 'undefined' ? 'unsupported' : Notification.permission),
  requestPermission: async () => (typeof Notification === 'undefined' ? 'unsupported' : Notification.requestPermission()),
  current: async () => {
    const manager = await pushManager();
    const subscription = await manager?.getSubscription();
    return subscription ? subscriptionRequest(subscription.toJSON()) : null;
  },
  subscribe: async publicKey => {
    const manager = await pushManager();
    if (!manager) throw new Error('이 브라우저에서는 알림을 켤 수 없어요');
    const subscription = (await manager.getSubscription())
      ?? (await manager.subscribe({ userVisibleOnly: true, applicationServerKey: base64UrlToBytes(publicKey) }));
    const request = subscriptionRequest(subscription.toJSON());
    if (!request) throw new Error('이 브라우저에서는 알림을 켤 수 없어요');
    return request;
  },
  unsubscribe: async () => {
    const manager = await pushManager();
    const subscription = await manager?.getSubscription();
    if (!subscription) return null;
    const endpoint = subscription.endpoint;
    await subscription.unsubscribe();
    return endpoint;
  },
};
