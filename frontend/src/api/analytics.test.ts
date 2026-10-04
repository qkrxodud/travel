/**
 * 화면 이벤트 수집 — 익명 방문 ID 로 허용된 이벤트만 모아 보내고, 개인정보는 싣지 않고, 실패해도 화면을 방해하지 않는다.
 * 이야기 순서: 익명 방문 ID → 허용된 이벤트만 → 개인정보 → 모아 보내기 → 화면이 숨겨지거나 닫힐 때 → 보내기에 실패할 때.
 */
import { describe, expect, it, vi } from 'vitest';
import { AnalyticsClient, CLIENT_EVENT_NAMES, errorToastCode, isPersonalKey, sanitizeProps, VISITOR_ID_KEY, type AnalyticsEnvironment } from './analytics';
import { ApiError } from './client';
import type { EventsRequest } from './types/analytics';

type Sent = { path: string; init: RequestInit | undefined; body: EventsRequest };

function memoryStorage(initial: Record<string, string> = {}) {
  const data = new Map(Object.entries(initial));
  return { getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => void data.set(key, value), data };
}

/** 가짜 브라우저: 보낸 요청·비콘을 모으고, 타이머는 손으로 돌린다 */
function browser(options: { respond?: (count: number) => Response | Promise<Response>; beacon?: boolean | null; storage?: ReturnType<typeof memoryStorage> | null; started?: boolean } = {}) {
  const sent: Sent[] = [];
  const beacons: { url: string; body: Blob }[] = [];
  const timers = new Map<number, () => void>();
  let timerIds = 0;
  const warnings: string[] = [];
  const storage = options.storage === undefined ? memoryStorage() : options.storage;
  let ids = 0;
  const env: AnalyticsEnvironment = {
    transport: async (path, init) => {
      sent.push({ path, init, body: JSON.parse(String(init?.body)) as EventsRequest });
      return options.respond ? options.respond(sent.length) : new Response(JSON.stringify({ accepted: 1, rejected: [] }), { status: 202 });
    },
    beacon: options.beacon === null ? null : (url, body) => {
      beacons.push({ url, body });
      return options.beacon ?? true;
    },
    storage,
    authHeaders: () => ({ 'X-Explorer-Token': 'secret-token' }),
    now: () => new Date('2026-10-04T03:00:00Z'),
    randomId: () => `visitor-${(ids += 1)}-abcdef`,
    schedule: callback => {
      timerIds += 1;
      timers.set(timerIds, callback);
      return timerIds;
    },
    cancel: handle => void timers.delete(handle as number),
    warn: message => void warnings.push(message),
  };
  const client = new AnalyticsClient(env);
  if (options.started !== false) client.start();
  /** 예약된 타이머를 돌리고 전송이 끝날 때까지 기다린다 */
  const tick = async () => {
    const due = [...timers.values()];
    timers.clear();
    due.forEach(callback => callback());
    await new Promise(resolve => setTimeout(resolve, 0));
  };
  const names = () => sent.flatMap(request => request.body.events.map(event => event.name));
  return { client, sent, beacons, timers, warnings, storage, tick, names };
}

const settle = () => new Promise(resolve => setTimeout(resolve, 0));

describe('익명 방문 ID', () => {
  it('처음 연 브라우저에서 무작위 값을 만들어 저장하고, 다음에 열어도 같은 값을 쓴다', () => {
    const storage = memoryStorage();
    const first = browser({ storage }).client.visitorId();
    expect(storage.data.get(VISITOR_ID_KEY)).toBe(first);
    expect(browser({ storage }).client.visitorId()).toBe(first);
  });

  it('저장된 값이 형식에 맞지 않으면 새로 만든다', () => {
    const storage = memoryStorage({ [VISITOR_ID_KEY]: 'bad id!' });
    expect(browser({ storage }).client.visitorId()).toMatch(/^[A-Za-z0-9_-]{8,64}$/);
    expect(storage.data.get(VISITOR_ID_KEY)).not.toBe('bad id!');
  });

  it('저장소를 쓸 수 없는 브라우저에서도 이 페이지 동안은 같은 값을 쓴다', () => {
    const { client } = browser({ storage: null });
    expect(client.visitorId()).toBe(client.visitorId());
  });

  it('탐험가 접근 토큰과는 무관한 값이고, 토큰은 인증 헤더로만 간다', async () => {
    const { client, sent, tick } = browser();
    client.track('checkin_open');
    await tick();
    expect(sent[0]?.body.visitorId).not.toContain('secret-token');
    expect(JSON.stringify(sent[0]?.body)).not.toContain('secret-token');
    expect((sent[0]?.init?.headers as Record<string, string>)['X-Explorer-Token']).toBe('secret-token');
  });
});

describe('허용된 이벤트만', () => {
  it('계약의 화면 이벤트 열 가지만 안다', () => {
    expect([...CLIENT_EVENT_NAMES].sort()).toEqual([
      'app_open', 'checkin_cancel', 'checkin_open', 'checkin_save', 'error_toast', 'link_copy', 'onboarding_step', 'push_prompt', 'share_click', 'tab_view',
    ]);
  });

  it('가입·체크인처럼 서버가 아는 사실이나 모르는 이름은 보내지 않는다', () => {
    expect(sanitizeProps('check_in', {})).toBeNull();
    expect(sanitizeProps('explorer_created', {})).toBeNull();
    expect(sanitizeProps('whatever', {})).toBeNull();
  });

  it('정해진 값 목록 밖이거나 형식이 틀린 값은 이벤트째 버린다', () => {
    expect(sanitizeProps('tab_view', { tab: 'map' })).toEqual({ tab: 'map' });
    expect(sanitizeProps('tab_view', { tab: 'secret' })).toBeNull();
    expect(sanitizeProps('app_open', {})).toBeNull();
    expect(sanitizeProps('share_click', { target: 'Territory Card' })).toBeNull();
    expect(sanitizeProps('onboarding_step', { step: 1.5, action: 'view' })).toBeNull();
    expect(sanitizeProps('onboarding_step', { step: 2, action: 'view' })).toEqual({ step: 2, action: 'view' });
  });

  it('정의되지 않은 필드가 섞이면 보내지 않는다', () => {
    expect(sanitizeProps('checkin_open', { region: 'KR-11010' })).toBeNull();
    expect(sanitizeProps('share_click', { target: 'profile', extra: 'x' })).toBeNull();
  });

  it('수집을 시작하기 전(관리자 화면 등)에 부른 기록은 버린다', async () => {
    const { client, sent, tick } = browser({ started: false });
    client.track('checkin_open');
    await tick();
    expect(client.pending).toBe(0);
    expect(sent).toHaveLength(0);
  });
});

describe('개인정보', () => {
  it('handle·메모·이메일·위치·토큰 같은 필드는 대소문자·_·- 를 무시하고 개인정보로 본다', () => {
    for (const key of ['handle', 'memo', 'E-mail', 'explorer_id', 'explorerId', 'Lat', 'accessToken', 'user-agent', 'photoRef', 'message']) {
      expect(isPersonalKey(key), key).toBe(true);
    }
    expect(isPersonalKey('target')).toBe(false);
  });

  it('개인정보 필드가 섞인 이벤트는 그 값이 요청에 실리지 않는다', async () => {
    const { client, sent, tick, warnings } = browser();
    const leaky = { target: 'profile', handle: 'kim_traveler', memo: '물회 먹음' } as unknown as { target: string };
    client.track('share_click', leaky);
    client.track('link_copy', { target: 'profile' });
    await tick();
    const body = JSON.stringify(sent.map(request => request.body));
    expect(body).not.toContain('kim_traveler');
    expect(body).not.toContain('물회');
    expect(sent[0]?.body.events.map(event => event.name)).toEqual(['link_copy']);
    expect(warnings).toHaveLength(1);
  });

  it('오류 토스트는 코드만 싣고 서버 메시지 문장은 싣지 않는다', () => {
    expect(errorToastCode(new ApiError(409, 'DUPLICATE_VISIT', '이미 칠한 곳이에요: 종로구 메모'))).toBe('DUPLICATE_VISIT');
    expect(errorToastCode(new ApiError(500, 'HTTP_500', '서버 오류'))).toBe('HTTP_500');
    expect(errorToastCode(new TypeError('Failed to fetch'))).toBe('NETWORK_ERROR');
    expect(errorToastCode('문장')).toBe('UNKNOWN_ERROR');
    expect(sanitizeProps('error_toast', { code: '이미 칠한 곳이에요' })).toBeNull();
  });
});

describe('모아 보내기', () => {
  it('바로 보내지 않고 5초 동안 모았다가 한 번에 보낸다(발생 시각과 함께)', async () => {
    const { client, sent, tick } = browser();
    client.track('app_open', { entry: 'direct' });
    client.track('tab_view', { tab: 'map' });
    expect(sent).toHaveLength(0);
    await tick();
    expect(sent).toHaveLength(1);
    expect(sent[0]?.path).toBe('/events');
    expect(sent[0]?.init?.method).toBe('POST');
    expect((sent[0]?.init?.headers as Record<string, string>)['Content-Type']).toBe('application/json');
    expect(sent[0]?.body.events).toEqual([
      { name: 'app_open', at: '2026-10-04T03:00:00.000Z', props: { entry: 'direct' } },
      { name: 'tab_view', at: '2026-10-04T03:00:00.000Z', props: { tab: 'map' } },
    ]);
  });

  it('20개가 차면 기다리지 않고 바로 보낸다', async () => {
    const { client, sent } = browser();
    for (let i = 0; i < 20; i++) client.track('checkin_open');
    await settle();
    expect(sent).toHaveLength(1);
    expect(sent[0]?.body.events).toHaveLength(20);
  });

  it('한 요청에는 50개까지만 싣는다', async () => {
    const { client, sent } = browser();
    for (let i = 0; i < 19; i++) client.track('checkin_open');
    // 첫 전송이 끝나기 전에 쌓인 것은 다음 요청으로
    for (let i = 0; i < 60; i++) client.track('checkin_cancel');
    await client.flush();
    expect(sent.every(request => request.body.events.length <= 50)).toBe(true);
    expect(sent.reduce((sum, request) => sum + request.body.events.length, 0)).toBe(79);
  });

  it('보낼 것이 없으면 요청하지 않는다', async () => {
    const { client, sent } = browser();
    await client.flush();
    expect(sent).toHaveLength(0);
  });

  it('보내지 못한 채 너무 많이 쌓이면 오래된 것부터 버린다(100개)', () => {
    const { client } = browser({ respond: () => new Promise(() => undefined) });
    void client.flush();
    for (let i = 0; i < 150; i++) client.track('checkin_open');
    expect(client.pending).toBeLessThanOrEqual(100);
  });
});

describe('화면이 숨겨지거나 닫힐 때', () => {
  function page() {
    const listeners = new Map<string, () => void>();
    const target = {
      visibilityState: 'visible' as DocumentVisibilityState,
      addEventListener: (type: string, listener: () => void) => void listeners.set(type, listener),
      removeEventListener: (type: string) => void listeners.delete(type),
    };
    return { target, fire: (type: string) => listeners.get(type)?.(), listeners };
  }

  it('다른 탭으로 가 화면이 숨겨지면 남은 것을 인증 헤더와 함께 keepalive 로 바로 보낸다', async () => {
    const { client, sent } = browser();
    const doc = page();
    const win = page();
    client.listen(doc.target as unknown as Document, win.target as unknown as Window);
    client.track('checkin_save');
    doc.target.visibilityState = 'hidden';
    doc.fire('visibilitychange');
    await settle();
    expect(sent).toHaveLength(1);
    expect(sent[0]?.init?.keepalive).toBe(true);
    expect((sent[0]?.init?.headers as Record<string, string>)['X-Explorer-Token']).toBe('secret-token');
  });

  it('페이지가 닫히면 sendBeacon 으로 JSON 을 보낸다', async () => {
    const { client, sent, beacons } = browser();
    const doc = page();
    const win = page();
    client.listen(doc.target as unknown as Document, win.target as unknown as Window);
    client.track('share_click', { target: 'territory' });
    win.fire('pagehide');
    expect(sent).toHaveLength(0);
    expect(beacons).toHaveLength(1);
    expect(beacons[0]?.url).toBe('/events');
    expect(beacons[0]?.body.type).toBe('application/json');
    const body = JSON.parse(await (beacons[0]?.body ?? new Blob()).text()) as EventsRequest;
    expect(body.events.map(event => event.name)).toEqual(['share_click']);
    expect(client.pending).toBe(0);
  });

  it('sendBeacon 이 없거나 거절하면 keepalive 요청으로 대신 보낸다', async () => {
    for (const beacon of [null, false] as const) {
      const { client, sent } = browser({ beacon });
      client.track('checkin_open');
      client.flushOnClose();
      await settle();
      expect(sent).toHaveLength(1);
      expect(sent[0]?.init?.keepalive).toBe(true);
    }
  });

  it('듣기를 그만두면 더는 반응하지 않는다', () => {
    const { client } = browser();
    const doc = page();
    const win = page();
    const stop = client.listen(doc.target as unknown as Document, win.target as unknown as Window);
    stop();
    expect(doc.listeners.size + win.listeners.size).toBe(0);
  });
});

describe('보내기에 실패할 때', () => {
  it('너무 잦음·너무 큼·서버 오류 응답은 다시 보내지 않고 버린다', async () => {
    for (const status of [429, 413, 400, 500, 503]) {
      const { client, sent, timers } = browser({ respond: () => new Response(null, { status }) });
      client.track('checkin_open');
      await client.flush();
      expect(sent).toHaveLength(1);
      expect(client.pending).toBe(0);
      expect(timers.size).toBe(0);
    }
  });

  it('네트워크가 끊겼으면 한 번 더 싣고, 그래도 안 되면 버린다', async () => {
    const respond = vi.fn<(count: number) => Response>(() => {
      throw new TypeError('Failed to fetch');
    });
    const { client, sent, tick } = browser({ respond });
    client.track('checkin_open');
    await client.flush();
    expect(client.pending).toBe(1);
    await tick();
    expect(sent).toHaveLength(2);
    expect(client.pending).toBe(0);
  });

  it('네트워크 실패 뒤 다시 실을 것은 소량(20개)만 남긴다', async () => {
    const { client } = browser({
      respond: () => {
        throw new TypeError('offline');
      },
    });
    for (let i = 0; i < 45; i++) client.track('checkin_open');
    await client.flush();
    expect(client.pending).toBeLessThanOrEqual(20);
  });

  it('서버가 받지 않은 이벤트는 개발 콘솔 경고로만 알리고 다시 보내지 않는다', async () => {
    const { client, sent, warnings } = browser({
      respond: () => new Response(JSON.stringify({ accepted: 0, rejected: [{ index: 0, name: 'checkin_open', reason: 'UNKNOWN_EVENT' }] }), { status: 202 }),
    });
    client.track('checkin_open');
    await client.flush();
    expect(warnings).toEqual(['서버가 받지 않은 분석 이벤트']);
    expect(sent).toHaveLength(1);
    expect(client.pending).toBe(0);
  });
});
