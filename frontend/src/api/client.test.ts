/**
 * 서버 통신 — 탐험가가 누구인지 알리고(익명 토큰·로그인 세션), 서버 오류를 화면 말로 옮기고, 지역 코드를 서로 바꾼다.
 * 이야기 순서: 지역 코드 → 처음 온 기기 → 익명 탐험가의 요청 → 로그인 → 로그아웃 → 서버 오류 → 인증이 필요 없는 것·특별한 요청.
 */
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
const loggedInSession = () => json(200, { googleLoginEnabled: true, loginUrl: '/oauth2', loggedIn: true, explorerId: 'account', handle: 'kim', email: null, personalMapId: 'p', mergeNotice: null });
const issued = (id: string) => json(201, { explorerId: id, personalMapId: 'p-' + id, anonymous: true, createdAt: '', accessToken: 'token-' + id, handle: null });

describe('지역 코드', () => {
  it('화면은 11010, 서버는 KR-11010 으로 부르고 서로 바꿔 준다', () => {
    expect(toServerCode('11010')).toBe('KR-11010');
    expect(toClientCode('KR-11010')).toBe('11010');
  });

  it('이미 바뀐 코드는 그대로 두고 시·도 코드도 같은 규칙을 따른다', () => {
    expect(toServerCode('KR-11010')).toBe('KR-11010');
    expect(toClientCode('11010')).toBe('11010');
    expect(toClientCode('KR-11')).toBe('11');
  });
});

describe('처음 온 기기', () => {
  it('첫 요청 전에 익명 탐험가를 발급받아 기억하고, 지도를 처음부터 보이게 알린다', async () => {
    const storage = memoryStorage();
    const { transport, calls } = fakeServer({
      'GET /auth/session': anonymousSession,
      'POST /explorers': (_init, count) => issued('e' + count),
      'GET /territory': () => json(200, {}),
    });
    const client = new ApiClient({ transport, storage, cookies: () => '' });
    const reset = vi.fn();
    client.onIdentityReset(reset);
    await client.request('GET', '/territory');
    expect(storage.data.get(EXPLORER_TOKEN_KEY)).toBe('token-e1');
    expect(storage.data.get(EXPLORER_ID_KEY)).toBe('e1');
    expect(header(calls[2], 'X-Explorer-Token')).toBe('token-e1');
    expect(reset).toHaveBeenCalledWith('issued');
  });

  it('여러 화면이 한꺼번에 불러도 탐험가는 하나만 발급받고 세션도 한 번만 확인한다', async () => {
    const storage = memoryStorage();
    const { transport, calls } = fakeServer({
      'GET /auth/session': anonymousSession,
      'POST /explorers': (_init, count) => issued('e' + count),
      'GET /territory': () => json(200, {}),
      'GET /progress': () => json(200, {}),
      'GET /maps': () => json(200, []),
    });
    const client = new ApiClient({ transport, storage, cookies: () => '' });
    await Promise.all([client.request('GET', '/territory'), client.request('GET', '/progress'), client.request('GET', '/maps')]);
    expect(calls.filter(call => call.path === '/explorers')).toHaveLength(1);
    expect(calls.filter(call => call.path === '/auth/session')).toHaveLength(1);
    expect(calls.filter(call => call.path !== '/explorers' && call.path !== '/auth/session').every(call => header(call, 'X-Explorer-Token') === 'token-e1')).toBe(true);
  });

  it('저장된 탐험가가 서버에서 사라졌으면 새로 발급받아 한 번만 다시 시도한다', async () => {
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

  it('다시 시도해도 실패하면 그 오류를 알리고 더 발급받지 않는다', async () => {
    const { transport, calls } = fakeServer({
      'GET /auth/session': anonymousSession,
      'POST /explorers': () => issued('again'),
      'GET /territory': () => json(404, { code: 'EXPLORER_NOT_FOUND', message: '없는 탐험가' }),
    });
    const client = new ApiClient({ transport, storage: memoryStorage({ [EXPLORER_TOKEN_KEY]: 'stale' }), cookies: () => '' });
    await expect(client.request('GET', '/territory')).rejects.toMatchObject({ code: 'EXPLORER_NOT_FOUND' });
    expect(calls.filter(call => call.path === '/explorers')).toHaveLength(1);
  });

  it('발급이 실패하면 탐험가 발급 실패로 알린다', async () => {
    const { transport } = fakeServer({
      'GET /auth/session': anonymousSession,
      'POST /explorers': () => json(503, {}),
    });
    const client = new ApiClient({ transport, storage: memoryStorage(), cookies: () => '' });
    await expect(client.request('GET', '/territory')).rejects.toMatchObject({ code: 'ISSUE_FAILED', message: '탐험가 발급에 실패했어요' });
  });

  it('사생활 보호 모드처럼 기기 저장소를 못 써도 이번 방문 동안은 같은 탐험가로 동작한다', async () => {
    const blocked = {
      getItem: () => { throw new Error('blocked'); },
      setItem: () => { throw new Error('blocked'); },
      removeItem: () => { throw new Error('blocked'); },
    };
    const { transport, calls } = fakeServer({
      'GET /auth/session': anonymousSession,
      'POST /explorers': () => issued('memory'),
      'GET /territory': () => json(200, {}),
      'GET /progress': () => json(200, {}),
    });
    const client = new ApiClient({ transport, storage: blocked, cookies: () => '' });
    await client.request('GET', '/territory');
    await client.request('GET', '/progress');
    expect(calls.filter(call => call.path === '/explorers')).toHaveLength(1);
    expect(header(calls[calls.length - 1], 'X-Explorer-Token')).toBe('token-memory');
  });
});

describe('익명 탐험가의 요청', () => {
  it('요청마다 비밀 토큰을 싣고, 데이터를 바꾸는 요청에만 위조 방지 값을 함께 보낸다', async () => {
    const { transport, calls } = fakeServer({
      'GET /auth/session': anonymousSession,
      'GET /territory': () => json(200, { ok: true }),
      'POST /visits': () => json(201, { ok: true }),
    });
    const client = new ApiClient({ transport, storage: memoryStorage({ [EXPLORER_TOKEN_KEY]: 'secret' }), cookies: () => 'a=1; XSRF-TOKEN=csrf%3Dvalue; b=2' });
    await client.request('GET', '/territory');
    await client.request('POST', '/visits', { regionCode: 'KR-11010' });
    const [, readCall, writeCall] = calls;
    expect(header(readCall, 'X-Explorer-Token')).toBe('secret');
    expect(header(readCall, 'X-XSRF-TOKEN')).toBeUndefined();
    expect(header(writeCall, 'X-Explorer-Token')).toBe('secret');
    expect(header(writeCall, 'X-XSRF-TOKEN')).toBe('csrf=value');
  });

  it('보내는 내용은 서버가 읽는 형식으로 싣고, 돌려줄 내용 없이 성공하면 빈 값으로 돌려준다', async () => {
    const { transport, calls } = fakeServer({
      'GET /auth/session': anonymousSession,
      'POST /visits': () => json(201, { ok: true }),
      'DELETE /visits/KR-11010': () => new Response(null, { status: 204 }),
    });
    const client = new ApiClient({ transport, storage: memoryStorage({ [EXPLORER_TOKEN_KEY]: 'secret' }), cookies: () => '' });
    await expect(client.request('POST', '/visits', { regionCode: 'KR-11010' })).resolves.toEqual({ ok: true });
    expect(await client.request('DELETE', '/visits/KR-11010')).toBeNull();
    expect(header(calls[1], 'Content-Type')).toBe('application/json');
    expect(calls[1].init?.body).toBe(JSON.stringify({ regionCode: 'KR-11010' }));
  });

  it('위조 방지 값은 쿠키에서 이름으로 찾고, 없으면 싣지 않는다', () => {
    expect(readCookie('x=1; XSRF-TOKEN=abc', 'XSRF-TOKEN')).toBe('abc');
    expect(readCookie('x=1', 'XSRF-TOKEN')).toBeNull();
  });
});

describe('로그인한 탐험가', () => {
  it('세션으로만 인증해 토큰을 싣지 않고, 계정 탐험가를 기억하며 익명 토큰은 버린다', async () => {
    const storage = memoryStorage({ [EXPLORER_TOKEN_KEY]: 'old-token', [EXPLORER_ID_KEY]: 'anon' });
    const { transport, calls } = fakeServer({
      'GET /auth/session': loggedInSession,
      'GET /progress': () => json(200, {}),
    });
    const client = new ApiClient({ transport, storage, cookies: () => '' });
    await client.request('GET', '/progress');
    expect(header(calls[1], 'X-Explorer-Token')).toBeUndefined();
    expect(storage.data.get(EXPLORER_ID_KEY)).toBe('account');
    expect(storage.data.has(EXPLORER_TOKEN_KEY)).toBe(false);
    expect(client.current).toMatchObject({ explorerId: 'account', token: null, loggedIn: true });
  });

  it('구글 로그인을 시작할 때 지금 익명 토큰을 함께 보내 익명 기록을 계정으로 옮길 수 있게 한다', async () => {
    const { transport, calls } = fakeServer({ 'POST /auth/login-intent': () => json(200, { redirectUrl: '/oauth2/authorization/google' }) });
    const client = new ApiClient({ transport, storage: memoryStorage({ [EXPLORER_TOKEN_KEY]: 'anon-token' }), cookies: () => 'XSRF-TOKEN=x' });
    await expect(client.loginIntent()).resolves.toEqual({ redirectUrl: '/oauth2/authorization/google' });
    expect(header(calls[0], 'X-Explorer-Token')).toBe('anon-token');
    expect(header(calls[0], 'X-XSRF-TOKEN')).toBe('x');
  });
});

describe('로그아웃', () => {
  it('기기 기억을 비우고 다음 요청에서 세션을 다시 확인해 새 익명 탐험가로 시작한다', async () => {
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

describe('서버 오류', () => {
  it('서버가 준 오류 종류와 안내 문구를 그대로 화면에 전한다', async () => {
    const { transport } = fakeServer({
      'GET /auth/session': anonymousSession,
      'GET /territory': () => json(422, { code: 'FUTURE_VISIT_DATE', message: '오늘 이후로 적을 수 없어요' }),
    });
    const client = new ApiClient({ transport, storage: memoryStorage({ [EXPLORER_TOKEN_KEY]: 't1', [EXPLORER_ID_KEY]: 'e1' }), cookies: () => '' });
    const failure = await client.request('GET', '/territory').catch((error: unknown) => error);
    expect(failure).toBeInstanceOf(ApiError);
    expect(failure).toMatchObject({ status: 422, code: 'FUTURE_VISIT_DATE', message: '오늘 이후로 적을 수 없어요' });
  });

  it('화면이 모르는 오류 종류는 일반 서버 오류로 묶는다', () => {
    expect(toErrorCode('DAILY_CAP_EXCEEDED', 422)).toBe('DAILY_CAP_EXCEEDED');
    expect(toErrorCode('SOMETHING_NEW', 409)).toBe('HTTP_409');
    expect(toErrorCode(undefined, 500)).toBe('HTTP_500');
  });

  it('설명 없이 실패하면 기본 안내 문구를 보여 준다', async () => {
    const { transport } = fakeServer({
      'GET /auth/session': anonymousSession,
      'GET /progress': () => new Response('oops', { status: 502 }),
    });
    const client = new ApiClient({ transport, storage: memoryStorage({ [EXPLORER_TOKEN_KEY]: 't1' }), cookies: () => '' });
    await expect(client.request('GET', '/progress')).rejects.toMatchObject({ code: 'HTTP_502', message: '서버 오류 (502)' });
  });
});

describe('인증이 필요 없는 정보와 그림', () => {
  it('지도·지역 카탈로그는 탐험가 확인 없이 바로 받는다', async () => {
    const { transport, calls } = fakeServer({ 'GET /catalog/regions': () => json(200, { type: 'FeatureCollection', features: [] }) });
    const client = new ApiClient({ transport, storage: memoryStorage(), cookies: () => '' });
    await expect(client.publicJson('/catalog/regions')).resolves.toMatchObject({ type: 'FeatureCollection' });
    expect(calls.map(call => call.path)).toEqual(['/catalog/regions']);
  });

  it('내 자랑 카드 그림은 탐험가 토큰을 실어 그림 그대로 받고, 실패하면 카드 안내 문구를 보여 준다', async () => {
    const { transport, calls } = fakeServer({
      'GET /auth/session': anonymousSession,
      'GET /me/cards/territory.png': (_init, count) => (count === 1 ? new Response('png', { status: 200 }) : new Response(null, { status: 500 })),
    });
    const client = new ApiClient({ transport, storage: memoryStorage({ [EXPLORER_TOKEN_KEY]: 'card-token' }), cookies: () => '' });
    const image = await client.image('/me/cards/territory.png');
    expect(image.size).toBe(3);
    expect(header(calls[1], 'X-Explorer-Token')).toBe('card-token');
    await expect(client.image('/me/cards/territory.png')).rejects.toMatchObject({ message: '카드를 불러오지 못했어요 (500)' });
  });
});
