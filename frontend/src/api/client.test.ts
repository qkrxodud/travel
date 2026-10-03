import { describe, expect, it, vi } from 'vitest';
import { ApiClient, ApiError, EXPLORER_ID_KEY, EXPLORER_TOKEN_KEY, readCookie, toClientCode, toErrorCode, toServerCode, type Transport } from './client';

type Call = { path: string; init: RequestInit | undefined };

function json(status: number, body: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function memoryStorage(initial: Record<string, string> = {}) {
  const data = new Map(Object.entries(initial));
  return {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => void data.set(key, value),
    removeItem: (key: string) => void data.delete(key),
    data,
  };
}

/** 경로별 응답을 정해 둔 가짜 서버 */
function fakeServer(routes: Record<string, (init: RequestInit | undefined, count: number) => Response>) {
  const calls: Call[] = [];
  const counts = new Map<string, number>();
  const transport: Transport = async (path, init) => {
    calls.push({ path, init });
    const method = init?.method ?? 'GET';
    const key = `${method} ${path}`;
    const count = (counts.get(key) ?? 0) + 1;
    counts.set(key, count);
    const route = routes[key];
    if (!route) throw new Error('예상 못 한 요청: ' + key);
    return route(init, count);
  };
  return { transport, calls };
}

const header = (call: Call, name: string) => (call.init?.headers as Record<string, string> | undefined)?.[name];
const anonymousSession = () => json(200, { googleLoginEnabled: false, loginUrl: null, loggedIn: false, explorerId: null, handle: null, email: null, personalMapId: null, mergeNotice: null });
const issued = (id: string) => json(201, { explorerId: id, personalMapId: 'p-' + id, anonymous: true, createdAt: '', accessToken: 'token-' + id, handle: null });

describe('지역 코드 변환', () => {
  it('화면 11010 ↔ 서버 KR-11010', () => {
    expect(toServerCode('11010')).toBe('KR-11010');
    expect(toServerCode('KR-11010')).toBe('KR-11010');
    expect(toClientCode('KR-11010')).toBe('11010');
    expect(toClientCode('11010')).toBe('11010');
    expect(toClientCode('KR-11')).toBe('11');
  });
});

describe('에러 코드 변환', () => {
  it('아는 서버 코드는 그대로, 모르는 코드·빈 값은 HTTP_{status}', () => {
    expect(toErrorCode('DAILY_CAP_EXCEEDED', 422)).toBe('DAILY_CAP_EXCEEDED');
    expect(toErrorCode('SOMETHING_NEW', 409)).toBe('HTTP_409');
    expect(toErrorCode(undefined, 500)).toBe('HTTP_500');
  });

  it('{code, message} 응답 → ApiError(status, code, message)', async () => {
    const { transport } = fakeServer({
      'GET /auth/session': anonymousSession,
      'GET /territory': () => json(422, { code: 'FUTURE_VISIT_DATE', message: '오늘 이후로 적을 수 없어요' }),
    });
    const client = new ApiClient({ transport, storage: memoryStorage({ [EXPLORER_TOKEN_KEY]: 't1', [EXPLORER_ID_KEY]: 'e1' }), cookies: () => '' });
    const failure = await client.request('GET', '/territory').catch((error: unknown) => error);
    expect(failure).toBeInstanceOf(ApiError);
    expect(failure).toMatchObject({ status: 422, code: 'FUTURE_VISIT_DATE', message: '오늘 이후로 적을 수 없어요' });
  });

  it('본문 없는 실패는 HTTP_{status} + 기본 문구', async () => {
    const { transport } = fakeServer({
      'GET /auth/session': anonymousSession,
      'GET /progress': () => new Response('oops', { status: 502 }),
    });
    const client = new ApiClient({ transport, storage: memoryStorage({ [EXPLORER_TOKEN_KEY]: 't1' }), cookies: () => '' });
    await expect(client.request('GET', '/progress')).rejects.toMatchObject({ code: 'HTTP_502', message: '서버 오류 (502)' });
  });
});

describe('인증 헤더·CSRF', () => {
  it('익명: 토큰 헤더, 변경 메서드에만 쿠키 XSRF-TOKEN → X-XSRF-TOKEN, 본문은 JSON', async () => {
    const { transport, calls } = fakeServer({
      'GET /auth/session': anonymousSession,
      'GET /territory': () => json(200, { ok: true }),
      'POST /visits': () => json(201, { ok: true }),
      'DELETE /visits/KR-11010': () => new Response(null, { status: 204 }),
    });
    const client = new ApiClient({ transport, storage: memoryStorage({ [EXPLORER_TOKEN_KEY]: 'secret' }), cookies: () => 'a=1; XSRF-TOKEN=csrf%3Dvalue; b=2' });
    await client.request('GET', '/territory');
    await client.request('POST', '/visits', { regionCode: 'KR-11010' });
    expect(await client.request('DELETE', '/visits/KR-11010')).toBeNull();
    const [, getCall, postCall] = calls;
    expect(header(getCall, 'X-Explorer-Token')).toBe('secret');
    expect(header(getCall, 'X-XSRF-TOKEN')).toBeUndefined();
    expect(header(postCall, 'X-Explorer-Token')).toBe('secret');
    expect(header(postCall, 'X-XSRF-TOKEN')).toBe('csrf=value');
    expect(header(postCall, 'Content-Type')).toBe('application/json');
    expect(postCall.init?.body).toBe(JSON.stringify({ regionCode: 'KR-11010' }));
  });

  it('로그인 세션이면 토큰 헤더를 보내지 않고 세션 탐험가 id 를 저장한다(익명 토큰은 버린다)', async () => {
    const storage = memoryStorage({ [EXPLORER_TOKEN_KEY]: 'old-token', [EXPLORER_ID_KEY]: 'anon' });
    const { transport, calls } = fakeServer({
      'GET /auth/session': () => json(200, { googleLoginEnabled: true, loginUrl: '/oauth2', loggedIn: true, explorerId: 'account', handle: 'kim', email: null, personalMapId: 'p', mergeNotice: null }),
      'GET /progress': () => json(200, {}),
    });
    const client = new ApiClient({ transport, storage, cookies: () => '' });
    await client.request('GET', '/progress');
    expect(header(calls[1], 'X-Explorer-Token')).toBeUndefined();
    expect(storage.data.get(EXPLORER_ID_KEY)).toBe('account');
    expect(storage.data.has(EXPLORER_TOKEN_KEY)).toBe(false);
  });

  it('readCookie', () => {
    expect(readCookie('x=1; XSRF-TOKEN=abc', 'XSRF-TOKEN')).toBe('abc');
    expect(readCookie('x=1', 'XSRF-TOKEN')).toBeNull();
  });
});

describe('익명 탐험가 발급', () => {
  it('토큰이 없으면 첫 요청 전에 한 번만 발급한다(동시 요청도 한 번) — 저장하고 지도 초기화를 알린다', async () => {
    const storage = memoryStorage();
    const { transport, calls } = fakeServer({
      'GET /auth/session': anonymousSession,
      'POST /explorers': (_init, count) => issued('e' + count),
      'GET /territory': () => json(200, {}),
      'GET /progress': () => json(200, {}),
      'GET /maps': () => json(200, []),
    });
    const client = new ApiClient({ transport, storage, cookies: () => '' });
    const reset = vi.fn();
    client.onIdentityReset(reset);
    await Promise.all([client.request('GET', '/territory'), client.request('GET', '/progress'), client.request('GET', '/maps')]);
    expect(calls.filter(call => call.path === '/explorers')).toHaveLength(1);
    expect(calls.filter(call => call.path === '/auth/session')).toHaveLength(1);
    expect(storage.data.get(EXPLORER_TOKEN_KEY)).toBe('token-e1');
    expect(storage.data.get(EXPLORER_ID_KEY)).toBe('e1');
    expect(calls.filter(call => call.path !== '/explorers' && call.path !== '/auth/session').every(call => header(call, 'X-Explorer-Token') === 'token-e1')).toBe(true);
    expect(reset).toHaveBeenCalledWith('issued');
  });

  it('저장된 탐험가가 서버에서 사라졌으면(EXPLORER_TOKEN_INVALID) 새로 발급받아 한 번만 다시 시도한다', async () => {
    const storage = memoryStorage({ [EXPLORER_TOKEN_KEY]: 'stale' });
    const { transport, calls } = fakeServer({
      'GET /auth/session': anonymousSession,
      'POST /explorers': () => issued('fresh'),
      'GET /territory': init => ((init?.headers as Record<string, string>)['X-Explorer-Token'] === 'stale'
        ? json(401, { code: 'EXPLORER_TOKEN_INVALID', message: '토큰이 맞지 않아요' })
        : json(200, { mapId: 'm' })),
    });
    const client = new ApiClient({ transport, storage, cookies: () => '' });
    await expect(client.request('GET', '/territory')).resolves.toEqual({ mapId: 'm' });
    expect(calls.map(call => `${call.init?.method ?? 'GET'} ${call.path}`)).toEqual(['GET /auth/session', 'GET /territory', 'POST /explorers', 'GET /territory']);
    expect(storage.data.get(EXPLORER_TOKEN_KEY)).toBe('token-fresh');
  });

  it('재시도도 실패하면 그 에러를 낸다(무한 재발급 없음)', async () => {
    const { transport, calls } = fakeServer({
      'GET /auth/session': anonymousSession,
      'POST /explorers': () => issued('again'),
      'GET /territory': () => json(404, { code: 'EXPLORER_NOT_FOUND', message: '없는 탐험가' }),
    });
    const client = new ApiClient({ transport, storage: memoryStorage({ [EXPLORER_TOKEN_KEY]: 'stale' }), cookies: () => '' });
    await expect(client.request('GET', '/territory')).rejects.toMatchObject({ code: 'EXPLORER_NOT_FOUND' });
    expect(calls.filter(call => call.path === '/explorers')).toHaveLength(1);
  });

  it('로그아웃하면 저장소를 비우고 다음 요청에서 세션을 다시 확인한다', async () => {
    const storage = memoryStorage({ [EXPLORER_TOKEN_KEY]: 't', [EXPLORER_ID_KEY]: 'e' });
    const { transport, calls } = fakeServer({
      'GET /auth/session': anonymousSession,
      'POST /logout': () => new Response(null, { status: 204 }),
      'POST /explorers': () => issued('next'),
      'GET /progress': () => json(200, {}),
    });
    const client = new ApiClient({ transport, storage, cookies: () => 'XSRF-TOKEN=x' });
    const reset = vi.fn();
    client.onIdentityReset(reset);
    await client.request('GET', '/progress');
    await client.logout();
    expect(header(calls.find(call => call.path === '/logout') as Call, 'X-XSRF-TOKEN')).toBe('x');
    expect(storage.data.size).toBe(0);
    expect(reset).toHaveBeenCalledWith('logged-out');
    await client.request('GET', '/progress');
    expect(calls.filter(call => call.path === '/auth/session')).toHaveLength(2);
    expect(storage.data.get(EXPLORER_ID_KEY)).toBe('next');
  });
});
