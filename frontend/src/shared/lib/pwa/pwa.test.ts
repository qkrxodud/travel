/**
 * PWA 브라우저 쪽 — 이 기기가 알림을 받을 수 있는지, 서버 키·구독 모양, 새 버전 서비스워커 안내와 새로고침, 홈 화면 설치 창.
 */
import { describe, expect, it, vi } from 'vitest';
import { isIosDevice, pushSupport, type DeviceTraits } from './device';
import { InstallPrompt, type DeferredInstallPrompt } from './installPrompt';
import { base64UrlToBytes, subscriptionRequest } from './pushKey';
import { ServiceWorkerClient } from './serviceWorkerClient';

const LOCAL_VAPID = 'BL4nehRk6sf8mdeCUNdZYgFJV8YQKhg_DBB2UPhEBtJhpCGph6jaB7u4cWesO4IFwVwnsate9RFv1cnqDuuXjgU';
const traits = (overrides: Partial<DeviceTraits> = {}): DeviceTraits => ({
  secure: true, serviceWorker: true, pushManager: true, notification: true, ios: false, standalone: false, workerEnabled: true, ...overrides,
});

describe('이 기기가 알림을 받을 수 있는지', () => {
  it('보안 출처에서 서비스워커·푸시·알림이 모두 되면 받을 수 있다', () => {
    expect(pushSupport(traits())).toBe('supported');
    expect(pushSupport(traits({ secure: false }))).toBe('unsupported');
    expect(pushSupport(traits({ pushManager: false }))).toBe('unsupported');
  });

  it('서비스워커를 등록하지 않는 개발 화면에서는 받지 않는다', () => {
    expect(pushSupport(traits({ workerEnabled: false }))).toBe('unsupported');
  });

  it('iPhone·iPad 는 홈 화면에 추가한 앱에서만 받는다', () => {
    expect(pushSupport(traits({ ios: true, pushManager: false }))).toBe('ios-needs-install');
    expect(pushSupport(traits({ ios: true, standalone: true }))).toBe('supported');
    expect(isIosDevice('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'iPhone', 5)).toBe(true);
    expect(isIosDevice('Mozilla/5.0 (Macintosh)', 'MacIntel', 5)).toBe(true);
    expect(isIosDevice('Mozilla/5.0 (Macintosh)', 'MacIntel', 0)).toBe(false);
  });
});

describe('서버 키와 구독 모양', () => {
  it('서버 공개 키(base64url 87자)를 P-256 비압축 점 65바이트로 바꾼다', () => {
    const bytes = base64UrlToBytes(LOCAL_VAPID);
    expect(bytes).toHaveLength(65);
    expect(bytes[0]).toBe(0x04);
  });

  it('브라우저 구독을 서버가 받는 모양 그대로 옮기고, 키가 빠졌으면 보내지 않는다', () => {
    const json = { endpoint: 'https://fcm.googleapis.com/fcm/send/abc', expirationTime: null, keys: { p256dh: 'p', auth: 'a' } };
    expect(subscriptionRequest(json)).toEqual(json);
    expect(subscriptionRequest({ endpoint: 'https://fcm.googleapis.com/fcm/send/abc', keys: {} })).toBeNull();
  });
});

class FakeTarget {
  private readonly listeners = new Map<string, ((event: Event & { data?: unknown }) => void)[]>();
  addEventListener(type: string, listener: (event: Event & { data?: unknown }) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }
  removeEventListener() {}
  emit(type: string, extra: object = {}) {
    this.dispatch(Object.assign(new Event(type), extra));
  }
  dispatch(event: Event) {
    for (const listener of this.listeners.get(event.type) ?? []) listener(event);
  }
}

function fakeWorker(state: string) {
  const worker = new FakeTarget() as FakeTarget & { state: string; postMessage: ReturnType<typeof vi.fn> };
  worker.state = state;
  worker.postMessage = vi.fn();
  return worker;
}

function fakeContainer(registration: object, controlled: boolean) {
  const container = new FakeTarget() as FakeTarget & { controller: object | null; register: ReturnType<typeof vi.fn>; ready: Promise<object> };
  container.controller = controlled ? {} : null;
  container.register = vi.fn(async () => registration);
  container.ready = Promise.resolve(registration);
  return container;
}

describe('새 버전 안내', () => {
  it('지금 화면을 이미 서비스워커가 맡고 있을 때 새 버전이 설치를 마치면 "새 버전이 있어요"를 띄운다', async () => {
    const registration = Object.assign(new FakeTarget(), { waiting: null, installing: null as unknown, update: vi.fn(async () => undefined) });
    const container = fakeContainer(registration, true);
    const client = new ServiceWorkerClient(container as unknown as ServiceWorkerContainer, vi.fn());
    const changed = vi.fn();
    client.onUpdate(changed);
    await client.register();
    expect(client.updateReady).toBe(false);
    const installing = fakeWorker('installing');
    registration.installing = installing;
    registration.emit('updatefound');
    installing.state = 'installed';
    installing.emit('statechange');
    expect(client.updateReady).toBe(true);
    expect(changed).toHaveBeenCalled();
  });

  it('처음 설치(맡은 서비스워커가 없음)는 새 버전이 아니라 안내하지 않는다', async () => {
    const registration = Object.assign(new FakeTarget(), { waiting: null, installing: null as unknown, update: vi.fn() });
    const client = new ServiceWorkerClient(fakeContainer(registration, false) as unknown as ServiceWorkerContainer, vi.fn());
    await client.register();
    const installing = fakeWorker('installed');
    registration.installing = installing;
    registration.emit('updatefound');
    installing.emit('statechange');
    expect(client.updateReady).toBe(false);
  });

  it('"새로고침"을 누르면 기다리던 새 버전으로 넘어간 뒤 화면을 다시 연다', async () => {
    const waiting = fakeWorker('installed');
    const registration = Object.assign(new FakeTarget(), { waiting, installing: null, update: vi.fn() });
    const container = fakeContainer(registration, true);
    const reload = vi.fn();
    const client = new ServiceWorkerClient(container as unknown as ServiceWorkerContainer, reload);
    await client.register();
    expect(client.updateReady).toBe(true);
    container.emit('controllerchange');
    expect(reload).not.toHaveBeenCalled();
    client.applyUpdate();
    expect(waiting.postMessage).toHaveBeenCalledWith({ type: 'SKIP_WAITING' });
    container.emit('controllerchange');
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('서비스워커가 푸시 구독이 바뀌었다고 알리면 화면에 전한다', async () => {
    const registration = Object.assign(new FakeTarget(), { waiting: null, installing: null, update: vi.fn() });
    const container = fakeContainer(registration, true);
    const client = new ServiceWorkerClient(container as unknown as ServiceWorkerContainer, vi.fn());
    const resync = vi.fn();
    client.onSubscriptionChange(resync);
    await client.register();
    container.emit('message', { data: { type: 'PUSH_SUBSCRIPTION_CHANGED' } });
    container.emit('message', { data: { type: 'OTHER' } });
    expect(resync).toHaveBeenCalledTimes(1);
  });

  it('서비스워커를 못 쓰는 브라우저에서는 등록하지 않고 구독도 찾지 않는다', async () => {
    const client = new ServiceWorkerClient(null, vi.fn());
    expect(await client.register()).toBeNull();
    expect(client.enabled).toBe(false);
    expect(await client.ready()).toBeNull();
  });
});

describe('홈 화면 설치 창', () => {
  it('브라우저가 준 설치 창을 미뤄 두었다가 배너에서 한 번만 띄우고, 설치되면 다시 권하지 않는다', async () => {
    const target = new FakeTarget();
    const install = new InstallPrompt();
    install.listen(target as unknown as Window);
    expect(install.available).toBe(false);
    const event = Object.assign(new Event('beforeinstallprompt', { cancelable: true }), {
      prompt: vi.fn(async () => undefined), userChoice: Promise.resolve({ outcome: 'accepted' as const }),
    }) as unknown as DeferredInstallPrompt;
    target.dispatch(event);
    expect(event.defaultPrevented).toBe(true);
    expect(install.available).toBe(true);
    expect(await install.prompt()).toBe('accepted');
    expect(install.available).toBe(false);
    expect(await install.prompt()).toBeNull();
    target.emit('appinstalled');
    expect(install.installed).toBe(true);
  });
});
