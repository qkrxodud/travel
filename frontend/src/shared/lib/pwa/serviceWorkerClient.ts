/**
 * 서비스워커 등록과 새 버전 안내(12단계). main.tsx 가 게임 화면에서 한 번 register 한다(빌드 산출물만).
 *
 * - 새 서비스워커가 설치를 마치고 기다리면(지금 화면을 이미 다른 버전이 맡고 있을 때) updateReady 가 true → 화면이 "새 버전이 있어요 — 새로고침".
 *   누르면 기다리는 워커에 SKIP_WAITING → 넘어가면(controllerchange) 새로고침. 누르지 않으면 다음 방문 때 넘어간다.
 * - 새 버전 확인: 등록할 때, 화면이 다시 보일 때, 한 시간마다(오래 켜 둔 홈 화면 앱).
 * - 서비스워커가 푸시 구독이 바뀌었다고 알리면(pushsubscriptionchange) 듣는 쪽에 전한다(화면이 서버에 다시 보낸다).
 */
const SW_PATH = '/sw.js';
const UPDATE_CHECK_MS = 60 * 60 * 1000;
const MESSAGE_SKIP_WAITING = 'SKIP_WAITING';
const MESSAGE_SUBSCRIPTION_CHANGED = 'PUSH_SUBSCRIPTION_CHANGED';

type Listener = () => void;

export class ServiceWorkerClient {
  private waiting: ServiceWorker | null = null;
  private registration: ServiceWorkerRegistration | null = null;
  private registering: Promise<ServiceWorkerRegistration | null> | null = null;
  private applying = false;
  private readonly updateListeners = new Set<Listener>();
  private readonly subscriptionListeners = new Set<Listener>();
  private readonly container: ServiceWorkerContainer | null;
  private readonly reload: () => void;

  constructor(container: ServiceWorkerContainer | null, reload: () => void) {
    this.container = container;
    this.reload = reload;
  }

  /** 서비스워커를 쓰는지(이 화면이 등록을 시작했고 브라우저가 지원) */
  get enabled(): boolean {
    return this.container !== null && this.registering !== null;
  }

  register(): Promise<ServiceWorkerRegistration | null> {
    if (!this.registering) this.registering = this.doRegister();
    return this.registering;
  }

  private async doRegister(): Promise<ServiceWorkerRegistration | null> {
    const container = this.container;
    if (!container) return null;
    container.addEventListener('controllerchange', () => {
      if (this.applying) this.reload();
    });
    container.addEventListener('message', event => {
      const data: unknown = event.data;
      if (data && typeof data === 'object' && (data as { type?: unknown }).type === MESSAGE_SUBSCRIPTION_CHANGED) this.subscriptionListeners.forEach(listener => listener());
    });
    let registration: ServiceWorkerRegistration;
    try {
      registration = await container.register(SW_PATH, { scope: '/', updateViaCache: 'none' });
    } catch {
      return null;
    }
    this.registration = registration;
    this.watch(registration);
    return registration;
  }

  /** 새 버전 확인(오래 켜 둔 화면) — 화면이 다시 보일 때와 한 시간마다 */
  keepChecking(page: Pick<Document, 'addEventListener' | 'visibilityState'>, schedule: (callback: () => void, ms: number) => unknown): void {
    const check = () => void this.registration?.update().catch(() => undefined);
    page.addEventListener('visibilitychange', () => {
      if (page.visibilityState === 'visible') check();
    });
    schedule(check, UPDATE_CHECK_MS);
  }

  /** 이 화면을 맡은 등록(서비스워커가 활성화될 때까지 기다린다). 서비스워커를 쓰지 않으면 null. */
  async ready(): Promise<ServiceWorkerRegistration | null> {
    const registered = this.container && this.registering ? await this.registering : null;
    if (!registered || !this.container) return null;
    return this.container.ready;
  }

  get updateReady(): boolean {
    return this.waiting !== null;
  }

  onUpdate(listener: Listener): () => void {
    this.updateListeners.add(listener);
    return () => this.updateListeners.delete(listener);
  }

  onSubscriptionChange(listener: Listener): () => void {
    this.subscriptionListeners.add(listener);
    return () => this.subscriptionListeners.delete(listener);
  }

  /** "새로고침" — 기다리는 새 버전으로 넘어간 뒤 다시 연다 */
  applyUpdate(): void {
    const waiting = this.waiting;
    if (!waiting) {
      this.reload();
      return;
    }
    this.applying = true;
    waiting.postMessage({ type: MESSAGE_SKIP_WAITING });
  }

  private watch(registration: ServiceWorkerRegistration): void {
    const controlled = () => !!this.container?.controller;
    if (registration.waiting && controlled()) this.setWaiting(registration.waiting);
    registration.addEventListener('updatefound', () => {
      const installing = registration.installing;
      if (!installing) return;
      installing.addEventListener('statechange', () => {
        if (installing.state === 'installed' && controlled()) this.setWaiting(installing);
      });
    });
  }

  private setWaiting(worker: ServiceWorker): void {
    this.waiting = worker;
    this.updateListeners.forEach(listener => listener());
  }
}

/** 앱 전체가 쓰는 하나 */
export const serviceWorkerClient = new ServiceWorkerClient(
  typeof navigator !== 'undefined' && 'serviceWorker' in navigator ? navigator.serviceWorker : null,
  () => location.reload(),
);
