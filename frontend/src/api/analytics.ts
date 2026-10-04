/**
 * 화면 이벤트 수집(10단계, 계약 _workspace/10_contracts.md §0·§1-1·§2). 분석은 관찰자다 — 보내기에 실패해도 화면에는 아무것도 알리지 않는다.
 *
 * - 익명 방문 ID: 브라우저가 처음 열릴 때 만든 무작위 값을 localStorage 에 둔다(쿠키 아님, 탐험가가 바뀌어도 그대로).
 * - 허용 목록: 계약의 화면 이벤트 이름과 그 필드만 보낸다. 정의되지 않은 필드·개인정보 키가 섞인 이벤트는 통째로 버린다.
 * - 전송: 메모리 큐에 모아 5초마다 또는 20개가 차면 한 번(fetch, 인증 헤더는 API 클라이언트와 같게). 화면이 숨겨지면 남은 것을
 *   keepalive fetch 로(인증 헤더를 실을 수 있다), 페이지가 닫히면 sendBeacon 으로(헤더 없음 — 같은 방문 ID 로 서버가 다시 묶는다).
 * - 실패: 429·413·4xx·5xx 는 버린다. 네트워크 오류만 소량(20개) 보관해 다음 전송에 한 번 더 싣고, 그래도 안 되면 버린다.
 */
import { apiClient, browserStorage, type Transport } from './client';
import type { ClientEventName, ClientEventProps, EventItem, EventsRequest, EventsResponse } from './types/analytics';

export const EVENTS_PATH = '/events';
/** localStorage 키 — 익명 방문 ID */
export const VISITOR_ID_KEY = 'territory-visitor-id';

const FLUSH_MS = 5000;
const FLUSH_SIZE = 20;
/** 한 요청에 싣는 최대 개수(서버 상한 50) */
const MAX_BATCH = 50;
/** 큐 상한 — 넘으면 오래된 것부터 버린다 */
const MAX_QUEUE = 100;
/** 네트워크 실패 뒤 다시 실을 최대 개수 */
const MAX_RETAINED = 20;
const MAX_ATTEMPTS = 2;

const VISITOR_ID = /^[A-Za-z0-9_-]{8,64}$/;
const IDENTIFIER = /^[a-z0-9][a-z0-9_-]{0,31}$/;
const ERROR_CODE = /^[A-Z][A-Z0-9_]{1,63}$/;

/**
 * 개인정보로 보는 필드 이름(대소문자·_·- 무시) — 서버와 같은 목록. 이런 키가 섞인 이벤트는 보내지 않는다.
 * 허용 목록에 없는 필드도 보내지 않으므로 이 목록은 두 번째 그물이다.
 */
const PERSONAL_KEYS: ReadonlySet<string> = new Set([
  'memo', 'note', 'handle', 'nickname', 'name', 'email', 'phone', 'ip', 'ipaddress', 'useragent', 'ua', 'token', 'accesstoken',
  'explorerid', 'accountid', 'userid', 'sessionid', 'lat', 'lng', 'lon', 'latitude', 'longitude', 'location', 'address', 'photo',
  'photoref', 'text', 'message',
]);

/** 필드 없는 이벤트는 이름만, 필드 있는 이벤트는 필드 객체 하나 */
export type TrackArgs<Name extends ClientEventName> = keyof ClientEventProps[Name] extends never ? [] : [ClientEventProps[Name]];

type FieldCheck = (value: unknown) => boolean;
type FieldRules = { [Name in ClientEventName]: { [Field in keyof ClientEventProps[Name]]-?: FieldCheck } };

const oneOf = (...values: readonly string[]): FieldCheck => value => typeof value === 'string' && values.includes(value);
const identifier: FieldCheck = value => typeof value === 'string' && IDENTIFIER.test(value);
const errorCode: FieldCheck = value => typeof value === 'string' && ERROR_CODE.test(value);
const smallInteger: FieldCheck = value => typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 1000;

/** 화면 이벤트 허용 목록 — 이름과 필드(모두 필수) */
const FIELD_RULES: FieldRules = {
  app_open: { entry: oneOf('direct', 'invite', 'profile', 'card', 'other') },
  tab_view: { tab: oneOf('map', 'bag', 'sets', 'quests', 'rank', 'profile') },
  checkin_open: {},
  checkin_save: {},
  checkin_cancel: {},
  share_click: { target: identifier },
  link_copy: { target: identifier },
  onboarding_step: { step: smallInteger, action: oneOf('view', 'done', 'skip') },
  push_prompt: { result: oneOf('shown', 'granted', 'denied', 'dismissed') },
  error_toast: { code: errorCode },
};

export const CLIENT_EVENT_NAMES = Object.keys(FIELD_RULES) as readonly ClientEventName[];

const normalizeKey = (key: string): string => key.toLowerCase().replace(/[_-]/g, '');

/** 개인정보로 보는 필드 이름인지(대소문자·_·- 무시) */
export function isPersonalKey(key: string): boolean {
  return PERSONAL_KEYS.has(normalizeKey(key));
}

function isClientEventName(name: string): name is ClientEventName {
  return Object.prototype.hasOwnProperty.call(FIELD_RULES, name);
}

/** 허용 목록에 맞으면 보낼 필드(정의된 것만 복사), 아니면 null. */
export function sanitizeProps(name: string, props: unknown): Record<string, string | number> | null {
  if (!isClientEventName(name)) return null;
  const rules: Readonly<Record<string, FieldCheck>> = FIELD_RULES[name];
  const given: Record<string, unknown> = props && typeof props === 'object' ? (props as Record<string, unknown>) : {};
  const keys = Object.keys(given);
  if (keys.some(isPersonalKey) || keys.some(key => !(key in rules))) return null;
  const clean: Record<string, string | number> = {};
  for (const [field, check] of Object.entries(rules)) {
    const value = given[field];
    if (!check(value)) return null;
    clean[field] = value as string | number;
  }
  return clean;
}

/** 오류 토스트에 싣는 코드 — 서버 코드(·HTTP_{status}) 그대로, 네트워크 실패는 NETWORK_ERROR. 문장은 싣지 않는다. */
export function errorToastCode(error: unknown): string {
  const code = error && typeof error === 'object' ? (error as { code?: unknown }).code : undefined;
  if (typeof code === 'string' && ERROR_CODE.test(code)) return code;
  if (error instanceof TypeError) return 'NETWORK_ERROR';
  return 'UNKNOWN_ERROR';
}

export interface AnalyticsEnvironment {
  transport: Transport;
  /** navigator.sendBeacon(없으면 null) */
  beacon: ((url: string, body: Blob) => boolean) | null;
  storage: Pick<Storage, 'getItem' | 'setItem'> | null;
  /** 지금 인증 헤더(익명 토큰) — 발급을 일으키지 않는다. 로그인 세션은 쿠키로 함께 간다. */
  authHeaders: () => Record<string, string>;
  now: () => Date;
  randomId: () => string;
  schedule: (callback: () => void, ms: number) => unknown;
  cancel: (handle: unknown) => void;
  /** 서버가 받지 않은 이벤트(개발 콘솔 경고만) */
  warn: (message: string, detail?: unknown) => void;
}

interface Queued {
  item: EventItem;
  attempts: number;
}

type SendMode = 'normal' | 'keepalive';

export class AnalyticsClient {
  private readonly env: AnalyticsEnvironment;
  private queue: Queued[] = [];
  private timer: unknown = null;
  /** 보통 전송의 차례(앞 전송이 끝나야 다음) */
  private chain: Promise<void> = Promise.resolve();
  private flushQueued = false;
  private started = false;
  private visitor: string | null = null;

  constructor(env: AnalyticsEnvironment) {
    this.env = env;
  }

  /** 수집 시작(게임 화면만 — 관리자 화면은 시작하지 않는다). 시작 전의 track 은 버린다. */
  start(): void {
    this.started = true;
  }

  get pending(): number {
    return this.queue.length;
  }

  /** 익명 방문 ID — 저장된 값이 형식에 맞으면 그대로, 아니면 새로 만들어 저장한다. 저장소를 못 쓰면 이 페이지 동안만. */
  visitorId(): string {
    if (this.visitor) return this.visitor;
    let saved: string | null;
    try {
      saved = this.env.storage?.getItem(VISITOR_ID_KEY) ?? null;
    } catch {
      saved = null;
    }
    if (saved && VISITOR_ID.test(saved)) {
      this.visitor = saved;
      return saved;
    }
    const created = this.env.randomId();
    try {
      this.env.storage?.setItem(VISITOR_ID_KEY, created);
    } catch {
      // 사생활 보호 모드 — 메모리 값만
    }
    this.visitor = created;
    return created;
  }

  /** 이벤트 하나를 큐에 넣는다. 허용 목록에 맞지 않으면 버린다(개발 콘솔 경고). */
  track<Name extends ClientEventName>(name: Name, ...args: TrackArgs<Name>): void {
    if (!this.started) return;
    const props = sanitizeProps(name, args[0] ?? {});
    if (!props) {
      this.env.warn(`분석 이벤트를 보내지 않음: ${name}`);
      return;
    }
    this.queue.push({ item: { name, at: this.env.now().toISOString(), props }, attempts: 0 });
    if (this.queue.length > MAX_QUEUE) this.queue.splice(0, this.queue.length - MAX_QUEUE);
    if (this.queue.length >= FLUSH_SIZE && !this.flushQueued) void this.flush();
    else this.arm();
  }

  /**
   * 큐를 지금 보낸다(한 요청에 50개씩). 보통 전송은 앞 전송이 끝난 뒤 차례로, keepalive(화면이 숨겨질 때)는 기다리지 않고 바로.
   * 지금 큐에 있는 것만 보낸다 — 실패해 다시 실을 것은 다음 차례(5초 뒤)로 미룬다.
   */
  flush(mode: SendMode = 'normal'): Promise<void> {
    this.disarm();
    if (mode === 'keepalive') return this.drain('keepalive');
    this.flushQueued = true;
    this.chain = this.chain.then(() => this.drain('normal'));
    return this.chain;
  }

  private async drain(mode: SendMode): Promise<void> {
    if (mode === 'normal') this.flushQueued = false;
    const pending = this.queue.splice(0);
    for (let i = 0; i < pending.length; i += MAX_BATCH) await this.send(pending.slice(i, i + MAX_BATCH), mode);
  }

  /** 페이지가 닫힐 때: 남은 것을 sendBeacon 으로(못 쓰면 keepalive fetch). */
  flushOnClose(): void {
    this.disarm();
    while (this.queue.length) {
      const batch = this.queue.splice(0, MAX_BATCH);
      const body = this.body(batch);
      const sent = this.env.beacon ? this.safeBeacon(body) : false;
      if (!sent) void this.send(batch, 'keepalive');
    }
  }

  /** 화면이 숨겨지면 keepalive 로, 페이지가 닫히면 sendBeacon 으로 보내는 듣기 — 해제 함수를 돌려준다. */
  listen(page: Pick<Document, 'addEventListener' | 'removeEventListener' | 'visibilityState'>, view: Pick<Window, 'addEventListener' | 'removeEventListener'>): () => void {
    const onVisibility = () => {
      if (page.visibilityState === 'hidden') void this.flush('keepalive');
    };
    const onPageHide = () => this.flushOnClose();
    page.addEventListener('visibilitychange', onVisibility);
    view.addEventListener('pagehide', onPageHide);
    return () => {
      page.removeEventListener('visibilitychange', onVisibility);
      view.removeEventListener('pagehide', onPageHide);
    };
  }

  private safeBeacon(body: string): boolean {
    try {
      return this.env.beacon?.(EVENTS_PATH, new Blob([body], { type: 'application/json' })) ?? false;
    } catch {
      return false;
    }
  }

  private body(batch: readonly Queued[]): string {
    const request: EventsRequest = { visitorId: this.visitorId(), events: batch.map(queued => queued.item) };
    return JSON.stringify(request);
  }

  private async send(batch: Queued[], mode: SendMode): Promise<void> {
    let response: Response;
    try {
      response = await this.env.transport(EVENTS_PATH, {
        method: 'POST',
        headers: { ...this.env.authHeaders(), 'Content-Type': 'application/json' },
        body: this.body(batch),
        keepalive: mode === 'keepalive',
      });
    } catch {
      this.retain(batch);
      return;
    }
    // 4xx·5xx(레이트 리밋·크기 초과 포함)는 다시 보내지 않는다
    if (!response.ok) return;
    try {
      const result = (await response.json()) as EventsResponse;
      if (result.rejected?.length) this.env.warn('서버가 받지 않은 분석 이벤트', result.rejected);
    } catch {
      // 본문 없음 — 상관없다
    }
  }

  /** 네트워크 실패: 한 번 더 실을 것만 소량 보관(새 이벤트보다 앞에) */
  private retain(batch: readonly Queued[]): void {
    const again = batch
      .map(queued => ({ item: queued.item, attempts: queued.attempts + 1 }))
      .filter(queued => queued.attempts < MAX_ATTEMPTS)
      .slice(-MAX_RETAINED);
    if (!again.length) return;
    this.queue = [...again, ...this.queue].slice(-MAX_QUEUE);
    this.arm();
  }

  private arm(): void {
    if (this.timer === null && this.queue.length) {
      this.timer = this.env.schedule(() => {
        this.timer = null;
        void this.flush();
      }, FLUSH_MS);
    }
  }

  private disarm(): void {
    if (this.timer !== null) this.env.cancel(this.timer);
    this.timer = null;
  }
}

function randomVisitorId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
}

/** 앱 전체가 쓰는 수집기 하나(main.tsx 가 게임 화면에서만 start). */
export const analytics = new AnalyticsClient({
  transport: (input, init) => fetch(input, init),
  beacon: typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function' ? (url, body) => navigator.sendBeacon(url, body) : null,
  storage: browserStorage(),
  authHeaders: () => apiClient.passiveAuthHeaders(),
  now: () => new Date(),
  randomId: randomVisitorId,
  schedule: (callback, ms) => setTimeout(callback, ms),
  cancel: handle => clearTimeout(handle as ReturnType<typeof setTimeout>),
  warn: (message, detail) => {
    if (import.meta.env.DEV) console.warn(message, detail ?? '');
  },
});

/** 화면 이벤트 기록(컴포넌트·훅은 이것만 부른다) */
export function track<Name extends ClientEventName>(name: Name, ...args: TrackArgs<Name>): void {
  analytics.track(name, ...args);
}
