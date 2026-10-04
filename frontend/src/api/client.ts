/**
 * 서버 통신의 유일한 계층(fetch 래퍼).
 *
 * - 인증: 익명 탐험가는 발급(POST /explorers) 때 받은 비밀 접근 토큰을 X-Explorer-Token 헤더로 보낸다(explorerId 는 공개 식별자).
 *   로그인 세션(쿠키)이 있으면 세션으로 인증하고 토큰 헤더는 보내지 않는다(4단계). 세션 여부는 첫 요청 전에 한 번 확인한다.
 * - CSRF: 변경 메서드에는 쿠키 XSRF-TOKEN 값을 X-XSRF-TOKEN 헤더로 싣는다.
 * - 에러: 서버 {code, message} → ApiError(status, code, message). 모르는 코드는 HTTP_{status}.
 * - 저장된 탐험가가 서버에서 사라졌으면(데이터 초기화·병합) 새로 발급받아 한 번만 다시 시도한다.
 * - 지역 코드: 서버 KR-11010 ↔ 화면 11010 변환은 이 파일의 toServerCode/toClientCode 로만 한다.
 */
import type { SessionResponse } from './types/account';
import { SERVER_ERROR_CODES, type ErrorCode, type ServerErrorCode } from './types/common';
import type { ExplorerResponse } from './types/exploration';

export const EXPLORER_TOKEN_HEADER = 'X-Explorer-Token';
export const ADMIN_TOKEN_HEADER = 'X-Admin-Token';
export const CSRF_HEADER = 'X-XSRF-TOKEN';
export const CSRF_COOKIE = 'XSRF-TOKEN';
/** localStorage 키 — E2E fixture(dev.explorerOf·idOf)가 같은 키를 읽는다. 바꾸지 않는다. */
export const EXPLORER_ID_KEY = 'territory-explorer-id';
export const EXPLORER_TOKEN_KEY = 'territory-explorer-token';

const COUNTRY_PREFIX = 'KR-';
/** 이 코드로 실패하면 저장된 익명 탐험가를 버리고 새로 발급받아 한 번 다시 시도한다. */
const REISSUE_CODES: readonly ErrorCode[] = ['EXPLORER_NOT_FOUND', 'EXPLORER_TOKEN_REQUIRED', 'EXPLORER_TOKEN_INVALID', 'ACCOUNT_NOT_FOUND'];
const SAFE_METHODS = ['GET', 'HEAD'];

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

/** 화면 지역 코드(11010) → 서버 지역 코드(KR-11010). 이미 서버 코드면 그대로. */
export function toServerCode(code: string): string {
  return code.startsWith(COUNTRY_PREFIX) ? code : COUNTRY_PREFIX + code;
}

/** 서버 지역·시도 코드(KR-11010) → 화면 코드(11010). */
export function toClientCode(code: string): string {
  return code.startsWith(COUNTRY_PREFIX) ? code.slice(COUNTRY_PREFIX.length) : code;
}

const KNOWN_CODES: ReadonlySet<string> = new Set(SERVER_ERROR_CODES);

function isServerErrorCode(value: unknown): value is ServerErrorCode {
  return typeof value === 'string' && KNOWN_CODES.has(value);
}

/** 서버 에러 본문의 code → 화면 에러 코드(모르는 값·없는 값은 HTTP_{status}). */
export function toErrorCode(raw: unknown, status: number): ErrorCode {
  return isServerErrorCode(raw) ? raw : `HTTP_${status}`;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: ErrorCode;

  constructor(status: number, code: ErrorCode, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

/** 실패 응답 본문을 {code, message} 로 읽는다(본문이 없거나 JSON 이 아니면 빈 값). */
async function readError(response: Response): Promise<{ code: unknown; message: unknown }> {
  try {
    const body: unknown = await response.json();
    if (body && typeof body === 'object') {
      const record = body as Record<string, unknown>;
      return { code: record.code, message: record.message };
    }
  } catch {
    // 본문 없음 — 상태 코드로 대신한다
  }
  return { code: undefined, message: undefined };
}

async function toApiError(response: Response, fallbackMessage: string): Promise<ApiError> {
  const { code, message } = await readError(response);
  return new ApiError(response.status, toErrorCode(code, response.status), typeof message === 'string' && message ? message : fallbackMessage);
}

/** 쿠키 문자열에서 한 값을 읽는다. */
export function readCookie(cookieHeader: string, name: string): string | null {
  const hit = cookieHeader.split('; ').find(part => part.startsWith(name + '='));
  return hit ? decodeURIComponent(hit.slice(name.length + 1)) : null;
}

export interface Credentials {
  explorerId: string | null;
  token: string | null;
  loggedIn: boolean;
}

export type Transport = (input: string, init?: RequestInit) => Promise<Response>;

export interface ClientEnvironment {
  transport: Transport;
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> | null;
  cookies: () => string;
}

type IdentityListener = (reason: 'issued' | 'logged-out') => void;

export class ApiClient {
  private readonly env: ClientEnvironment;
  private credentials: Credentials;
  private sessionPromise: Promise<SessionResponse | null> | null = null;
  private issuing: Promise<ExplorerResponse> | null = null;
  private readonly identityListeners = new Set<IdentityListener>();

  constructor(env: ClientEnvironment) {
    this.env = env;
    this.credentials = {
      explorerId: this.read(EXPLORER_ID_KEY),
      token: this.read(EXPLORER_TOKEN_KEY),
      loggedIn: false,
    };
  }

  /** 지금 인증 상태(읽기 전용 사본). */
  get current(): Credentials {
    return { ...this.credentials };
  }

  /** 새 익명 탐험가 발급·로그아웃처럼 "다른 탐험가가 된" 순간을 알린다(지금 보는 지도를 개인 지도로 되돌리는 데 쓴다). */
  onIdentityReset(listener: IdentityListener): () => void {
    this.identityListeners.add(listener);
    return () => this.identityListeners.delete(listener);
  }

  /** 로그인 세션 상태 — 앱 수명 동안 한 번만 읽는다(병합 안내 mergeNotice 는 서버가 한 번 보여 주고 지운다). */
  session(): Promise<SessionResponse | null> {
    if (!this.sessionPromise) this.sessionPromise = this.fetchSession();
    return this.sessionPromise;
  }

  /** 인증이 필요 없는 공개 JSON(카탈로그) — 세션 확인·발급 없이 바로 읽는다. */
  async publicJson<T>(path: string): Promise<T> {
    const response = await this.env.transport(path);
    if (!response.ok) throw await toApiError(response, `서버 오류 (${response.status})`);
    return (await response.json()) as T;
  }

  /** JSON API 호출. 204 면 null. */
  async request<T>(method: HttpMethod, path: string, body?: unknown): Promise<T> {
    return this.send<T>(method, path, body, false);
  }

  /** 인증이 필요한 이미지(내 카드 PNG) — <img src> 는 헤더를 못 실으므로 fetch 로 받아 Blob 으로 넘긴다. */
  async image(path: string): Promise<Blob> {
    await this.ready();
    const response = await this.env.transport(path, { headers: this.authHeaders() });
    if (!response.ok) throw await toApiError(response, `카드를 불러오지 못했어요 (${response.status})`);
    return response.blob();
  }

  /** 지금 인증 헤더(익명 토큰) — 세션 확인·발급을 일으키지 않는다(분석 이벤트 전송용). 로그인 세션은 쿠키로 함께 간다. */
  passiveAuthHeaders(): Record<string, string> {
    return this.authHeaders();
  }

  /**
   * 운영 API(X-Admin-Token) — 탐험가 발급·세션 확인 없이 보낸다(관리자 화면은 게임 탐험가를 만들지 않는다). 204 면 null.
   * 토큰은 부르는 쪽이 메모리에만 들고 있다가 넘긴다.
   */
  async admin<T>(method: HttpMethod, path: string, adminToken: string): Promise<T> {
    const headers: Record<string, string> = adminToken ? { [ADMIN_TOKEN_HEADER]: adminToken } : {};
    const response = await this.env.transport(path, { method, headers: this.withCsrf(method, headers) });
    if (!response.ok) throw await toApiError(response, `서버 오류 (${response.status})`);
    return (response.status === 204 ? null : await response.json()) as T;
  }

  /** 구글 로그인 시작: 지금 기기의 익명 탐험가를 세션에 기억시키고 이동할 주소를 받는다. */
  async loginIntent<T>(): Promise<T> {
    const { token, loggedIn } = this.credentials;
    const headers = this.withCsrf('POST', token && !loggedIn ? { [EXPLORER_TOKEN_HEADER]: token } : {});
    const response = await this.env.transport('/auth/login-intent', { method: 'POST', headers });
    if (!response.ok) throw await toApiError(response, `서버 오류 (${response.status})`);
    return (await response.json()) as T;
  }

  /** 로그아웃 — 이 기기는 다음 요청에서 새 익명 탐험가로 시작한다. */
  async logout(): Promise<void> {
    await this.env.transport('/logout', { method: 'POST', headers: this.withCsrf('POST', {}) });
    this.credentials = { explorerId: null, token: null, loggedIn: false };
    this.remember();
    this.sessionPromise = null;
    this.emit('logged-out');
  }

  private async fetchSession(): Promise<SessionResponse | null> {
    try {
      const response = await this.env.transport('/auth/session');
      if (!response.ok) return null;
      const session = (await response.json()) as SessionResponse;
      if (session.loggedIn) {
        // 로그인하면 서버가 익명 토큰을 무효화한다 — 세션으로만 인증한다
        this.credentials = { explorerId: session.explorerId, token: null, loggedIn: true };
        this.remember();
      } else {
        this.credentials = { ...this.credentials, loggedIn: false };
      }
      return session;
    } catch {
      return null;
    }
  }

  /** 첫 요청 전: 세션 확인 → 세션도 토큰도 없으면 익명 탐험가 발급. */
  private async ready(): Promise<void> {
    await this.session();
    if (!this.credentials.token && !this.credentials.loggedIn) await this.issue();
  }

  private async send<T>(method: HttpMethod, path: string, body: unknown, retried: boolean): Promise<T> {
    await this.ready();
    const tokenUsed = this.credentials.token;
    const headers = this.authHeaders();
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const response = await this.env.transport(path, {
      method,
      headers: this.withCsrf(method, headers),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (response.ok) return (response.status === 204 ? null : await response.json()) as T;
    const error = await toApiError(response, `서버 오류 (${response.status})`);
    if (!retried && REISSUE_CODES.includes(error.code)) {
      // 다른 요청이 이미 새로 발급받았으면 그 토큰으로, 아니면 직접 다시 발급받아 한 번만 재시도
      if (!this.credentials.token || this.credentials.token === tokenUsed) {
        this.credentials = { ...this.credentials, loggedIn: false, token: null };
        await this.issue();
      }
      return this.send<T>(method, path, body, true);
    }
    throw error;
  }

  /** 익명 탐험가 발급 — 동시에 여러 요청이 와도 한 번만 발급한다. */
  private issue(): Promise<ExplorerResponse> {
    if (!this.issuing) {
      this.issuing = this.fetchIssue().finally(() => {
        this.issuing = null;
      });
    }
    return this.issuing;
  }

  private async fetchIssue(): Promise<ExplorerResponse> {
    const response = await this.env.transport('/explorers', { method: 'POST', headers: this.withCsrf('POST', {}) });
    if (!response.ok) throw new ApiError(response.status, 'ISSUE_FAILED', '탐험가 발급에 실패했어요');
    const issued = (await response.json()) as ExplorerResponse;
    this.credentials = { explorerId: issued.explorerId, token: issued.accessToken, loggedIn: false };
    this.remember();
    this.emit('issued');
    return issued;
  }

  private authHeaders(): Record<string, string> {
    const { token, loggedIn } = this.credentials;
    return !loggedIn && token ? { [EXPLORER_TOKEN_HEADER]: token } : {};
  }

  private withCsrf(method: string, headers: Record<string, string>): Record<string, string> {
    if (!SAFE_METHODS.includes(method)) {
      const csrf = readCookie(this.env.cookies(), CSRF_COOKIE);
      if (csrf) headers[CSRF_HEADER] = csrf;
    }
    return headers;
  }

  private emit(reason: 'issued' | 'logged-out'): void {
    this.identityListeners.forEach(listener => listener(reason));
  }

  private read(key: string): string | null {
    try {
      return this.env.storage?.getItem(key) ?? null;
    } catch {
      return null;
    }
  }

  private remember(): void {
    const { explorerId, token } = this.credentials;
    try {
      if (explorerId) this.env.storage?.setItem(EXPLORER_ID_KEY, explorerId);
      else this.env.storage?.removeItem(EXPLORER_ID_KEY);
      if (token) this.env.storage?.setItem(EXPLORER_TOKEN_KEY, token);
      else this.env.storage?.removeItem(EXPLORER_TOKEN_KEY);
    } catch {
      // 저장소를 쓸 수 없는 환경(사생활 보호 모드 등) — 메모리 상태만으로 동작
    }
  }
}

export function browserStorage(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage;
  } catch {
    return null;
  }
}

/** 앱 전체가 쓰는 클라이언트 하나. */
export const apiClient = new ApiClient({
  transport: (input, init) => fetch(input, init),
  storage: browserStorage(),
  cookies: () => (typeof document === 'undefined' ? '' : document.cookie),
});
