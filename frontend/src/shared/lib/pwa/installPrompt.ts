/**
 * 홈 화면 설치(12단계) — 크로미움 계열은 beforeinstallprompt 를 미뤄 두었다가 배너의 "설치"에서 띄운다. 이벤트는 앱이 그려지기 전에
 * 올 수 있어 main.tsx 가 먼저 listen 한다. 설치되면(appinstalled) 배너를 다시 보이지 않는다.
 */
type Listener = () => void;

/** 크로미움 BeforeInstallPromptEvent(표준 타입 없음) */
export interface DeferredInstallPrompt extends Event {
  prompt(): Promise<void>;
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

export class InstallPrompt {
  private deferred: DeferredInstallPrompt | null = null;
  private installedNow = false;
  private readonly listeners = new Set<Listener>();
  private version = 0;

  listen(target: Pick<Window, 'addEventListener'>): void {
    target.addEventListener('beforeinstallprompt', event => {
      event.preventDefault();
      this.deferred = event as DeferredInstallPrompt;
      this.changed();
    });
    target.addEventListener('appinstalled', () => {
      this.deferred = null;
      this.installedNow = true;
      this.changed();
    });
  }

  get available(): boolean {
    return this.deferred !== null;
  }

  get installed(): boolean {
    return this.installedNow;
  }

  /** useSyncExternalStore 용 */
  readonly subscribe = (listener: Listener): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  readonly snapshot = (): number => this.version;

  /** 브라우저 설치 창 — 한 번 쓰면 다시 쓸 수 없다 */
  async prompt(): Promise<'accepted' | 'dismissed' | null> {
    const deferred = this.deferred;
    if (!deferred) return null;
    this.deferred = null;
    this.changed();
    await deferred.prompt();
    const choice = await deferred.userChoice;
    return choice.outcome;
  }

  private changed(): void {
    this.version += 1;
    this.listeners.forEach(listener => listener());
  }
}

export const installPrompt = new InstallPrompt();
