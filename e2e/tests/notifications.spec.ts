import { test, expect, EXPLORER_HEADER } from '../fixtures';
import type { APIRequestContext, BrowserContext, Page, Request } from '@playwright/test';

/**
 * 12단계 PWA + 웹 푸시 — 홈 화면에 설치할 수 있는 앱이고, 첫 체크인 뒤에 "알림 받을래요?"에 예라고 한 브라우저만 알림을 받는다.
 * 서버는 실제로 암호화·VAPID 서명해 보낸다. 헤드리스 크로미움은 실제 브라우저 푸시 서비스(FCM)로 구독할 수 없으므로 브라우저의 구독 함수만
 * 이 서버의 개발용 가짜 푸시 서비스(/dev/push/inbox/{상자}) 주소를 돌려주게 바꾸고(키는 진짜 P-256), 서비스워커가 알림을 띄우는 것은
 * 개발자 도구 프로토콜로 같은 본문의 푸시를 서비스워커에 넣어 확인한다.
 */

// 알림 권한·표시는 새 헤드리스 크로미움에서만 실제처럼 동작한다(기본 headless shell 은 Notification.permission 이 늘 denied)
test.use({ channel: 'chromium' });

const GAPYEONG = '31370';
const region = (page: Page, code: string) => page.locator(`path.region[data-code="${code}"]`);
const FAKE_PUSH_KEY = 'e2e-fake-push-subscription';

type SentEvent = { name: string; props?: Record<string, unknown> };

/** 화면이 POST /events 로 보낸 이벤트를 모은다 */
function collectEvents(page: Page) {
  const events: SentEvent[] = [];
  page.on('request', (request: Request) => {
    if (request.method() === 'POST' && new URL(request.url()).pathname === '/events') {
      events.push(...(JSON.parse(request.postData() ?? '{}').events ?? []));
    }
  });
  return events;
}

/**
 * 브라우저 구독 함수를 가짜 푸시 서비스 상자 주소로 바꾼다(키는 진짜 P-256 — 서버가 그 키로 암호화한다). 구독은 localStorage 에 두어
 * 새로고침해도 같은 구독이 보인다.
 */
async function useFakePushService(context: BrowserContext, box: string) {
  await context.addInitScript(({ box: boxName, storageKey }) => {
    const encode = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    type Saved = { endpoint: string; p256dh: string; auth: string };
    const wrap = (saved: Saved) => ({
      endpoint: saved.endpoint,
      expirationTime: null,
      options: { userVisibleOnly: true, applicationServerKey: null },
      getKey: () => null,
      toJSON: () => ({ endpoint: saved.endpoint, expirationTime: null, keys: { p256dh: saved.p256dh, auth: saved.auth } }),
      unsubscribe: async () => {
        localStorage.removeItem(storageKey);
        return true;
      },
    });
    const read = (): Saved | null => {
      const raw = localStorage.getItem(storageKey);
      return raw ? (JSON.parse(raw) as Saved) : null;
    };
    const manager = PushManager.prototype as unknown as Record<string, unknown>;
    manager.getSubscription = async () => {
      const saved = read();
      return saved ? wrap(saved) : null;
    };
    manager.subscribe = async () => {
      const existing = read();
      if (existing) return wrap(existing);
      const pair = await crypto.subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits']);
      const publicKey = new Uint8Array(await crypto.subtle.exportKey('raw', pair.publicKey));
      const saved = { endpoint: `${location.origin}/dev/push/inbox/${boxName}`, p256dh: encode(publicKey), auth: encode(crypto.getRandomValues(new Uint8Array(16))) };
      localStorage.setItem(storageKey, JSON.stringify(saved));
      return wrap(saved);
    };
  }, { box, storageKey: FAKE_PUSH_KEY });
}

async function openApp(page: Page, path = '/') {
  await page.goto(path);
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
}

/** 이 화면을 맡은 서비스워커가 준비될 때까지 */
async function workerScript(page: Page): Promise<string> {
  return page.evaluate(async () => (await navigator.serviceWorker.ready).active?.scriptURL ?? '');
}

async function checkInOnScreen(page: Page, code: string) {
  await region(page, code).dispatchEvent('click');
  await expect(page.locator('#checkin')).toBeVisible();
  await page.click('#ci-save');
  await expect(page.locator('#checkin')).toBeHidden();
}

async function preferences(request: APIRequestContext, token: string) {
  const response = await request.get('/push/preferences', { headers: { [EXPLORER_HEADER]: token } });
  expect(response.ok()).toBeTruthy();
  return response.json();
}

/** local 즉시 발송(오늘이 그날이 아니어도 — 동의·설정·하루 1개·멱등은 그대로) */
async function sendNow(request: APIRequestContext, kind: 'mystery' | 'streak' | 'season') {
  const response = await request.post('/dev/push/send', { data: { kind } });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json() as Promise<{ runs: { decisions: Record<string, number> }[]; dispatch: { outcomes: Record<string, number> } }>;
}

async function inbox(request: APIRequestContext, box: string): Promise<{ status: number; urgency: string; topic: string; vapid: boolean; contentEncoding: string }[]> {
  const response = await request.get(`/dev/push/inbox/${box}`);
  expect(response.ok()).toBeTruthy();
  return response.json();
}

/**
 * 개발자 도구 프로토콜로 이 화면을 맡은 서비스워커에 푸시를 넣는다(브라우저 푸시 서비스가 전해 준 것과 같은 이벤트).
 * 같은 브라우저의 앞 테스트 컨텍스트가 남긴 등록과 헷갈리지 않게, 이 화면(target)을 맡은 서비스워커 버전의 등록을 고른다.
 * 헤드리스에서 개발자 도구 푸시가 드물게 서비스워커까지 가지 않는 일이 있어(전달 응답은 성공) 알림이 보일 때까지 세 번까지 넣는다 —
 * 같은 tag 라 기기에서는 하나로 겹친다(실제 재전송과 같다).
 */
async function deliverToWorker(context: BrowserContext, page: Page, payload: { title: string }) {
  const cdp = await context.newCDPSession(page);
  const origin = new URL(page.url()).origin;
  const { targetInfo } = await cdp.send('Target.getTargetInfo');
  const registrationId = new Promise<string>(resolve => {
    cdp.on('ServiceWorker.workerVersionUpdated', ({ versions }) => {
      const mine = versions.find(version => version.controlledClients?.includes(targetInfo.targetId));
      if (mine) resolve(mine.registrationId);
    });
  });
  await cdp.send('ServiceWorker.enable');
  const id = await registrationId;
  for (let attempt = 0; attempt < 3; attempt++) {
    await cdp.send('ServiceWorker.deliverPushMessage', { origin, registrationId: id, data: JSON.stringify(payload) });
    const shown = await expect.poll(async () => (await shownNotifications(page)).map(notification => notification.title), { timeout: 5000 })
      .toContain(payload.title).then(() => true, () => false);
    if (shown) return;
  }
}

async function shownNotifications(page: Page) {
  return page.evaluate(async () => {
    const registration = await navigator.serviceWorker.ready;
    return (await registration.getNotifications()).map(notification => ({ title: notification.title, body: notification.body, tag: notification.tag, data: notification.data }));
  });
}

test.describe('홈 화면에 설치할 수 있는 앱', () => {
  test('이름·아이콘·테마색·standalone 을 갖춘 매니페스트와 서비스워커가 있어 브라우저가 설치 가능하다고 본다', async ({ page, context, request }) => {
    await test.step('매니페스트에 "나의 영토"·standalone·192/512 아이콘이 있고 아이콘이 실제로 내려온다', async () => {
      const manifest = await (await request.get('/manifest.json')).json();
      expect(manifest).toMatchObject({ name: '나의 영토', short_name: '나의 영토', display: 'standalone', start_url: '/', theme_color: '#0f8f7e' });
      const sizes = manifest.icons.map((icon: { sizes: string }) => icon.sizes);
      expect(sizes).toEqual(expect.arrayContaining(['192x192', '512x512']));
      expect(manifest.icons.some((icon: { purpose?: string }) => icon.purpose === 'maskable')).toBe(true);
      for (const icon of manifest.icons) {
        const response = await request.get(icon.src);
        expect(response.ok(), icon.src).toBeTruthy();
        expect(response.headers()['content-type']).toContain('image/png');
      }
    });

    await test.step('첫 화면이 서비스워커(/sw.js)를 등록하고 화면을 맡긴다', async () => {
      await openApp(page);
      expect(await workerScript(page)).toMatch(/\/sw\.js$/);
      await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);
      await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', '#0f8f7e');
    });

    await test.step('브라우저가 보는 설치 조건에 걸리는 것이 없다', async () => {
      const cdp = await context.newCDPSession(page);
      const { installabilityErrors } = await cdp.send('Page.getInstallabilityErrors');
      // Playwright 컨텍스트는 시크릿 창이라 "in-incognito" 하나는 늘 붙는다 — 앱 쪽 조건(매니페스트·아이콘·서비스워커)만 본다
      expect(installabilityErrors.filter(error => error.errorId !== 'in-incognito')).toEqual([]);
      const { url, errors } = await cdp.send('Page.getAppManifest');
      expect(url).toMatch(/\/manifest\.json$/);
      expect(errors).toEqual([]);
    });

    await test.step('화면·API 는 서비스워커가 캐시하지 않고, 해시가 붙은 정적 자산만 캐시한다', async () => {
      await page.reload();
      await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
      const cached = await page.evaluate(async () => {
        const urls: string[] = [];
        for (const name of await caches.keys()) {
          const cache = await caches.open(name);
          urls.push(...(await cache.keys()).map(entry => new URL(entry.url).pathname));
        }
        return urls;
      });
      expect(cached.some(path => path.startsWith('/assets/'))).toBe(true);
      expect(cached.filter(path => !path.startsWith('/assets/') && !path.startsWith('/icons/') && path !== '/manifest.json')).toEqual([]);
    });
  });

  test('두 번째로 왔고 첫 체크인을 했으면 설치를 권하고, 닫으면 다시 권하지 않는다', async ({ page, context }) => {
    // 지난번 방문은 두 시간 전(30분 넘게 쉬었다 다시 열면 새 방문)
    await context.addInitScript(() => {
      if (!sessionStorage.getItem('e2e-visit-seeded')) {
        sessionStorage.setItem('e2e-visit-seeded', '1');
        localStorage.setItem('territory-visit-log', JSON.stringify({ count: 1, lastSeenAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString() }));
      }
    });
    await openApp(page);
    // 크로미움이 설치 창을 준비했다고 알리는 이벤트(헤드리스에서는 저절로 오지 않을 수 있어 같은 모양으로 보낸다)
    const offerInstall = () => page.evaluate(() => {
      const event = new Event('beforeinstallprompt', { cancelable: true }) as Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: string }> };
      event.prompt = async () => {
        document.documentElement.dataset.installPrompted = 'true';
      };
      event.userChoice = Promise.resolve({ outcome: 'dismissed' });
      window.dispatchEvent(event);
    });

    await test.step('두 번째 방문이어도 아직 칠한 곳이 없으면 권하지 않는다', async () => {
      await offerInstall();
      await expect(page.locator('#install-banner')).toHaveCount(0);
    });

    await test.step('첫 체크인을 하면 설치를 권하고, 설치하기를 누르면 브라우저 설치 창을 띄운다', async () => {
      await checkInOnScreen(page, GAPYEONG);
      await expect(page.locator('#install-banner')).toBeVisible();
      await page.click('#install-accept');
      await expect(page.locator('html')).toHaveAttribute('data-install-prompted', 'true');
      // 설치 창에서 취소했다 → 닫은 것으로 보고 숨긴다
      await expect(page.locator('#install-banner')).toHaveCount(0);
    });

    await test.step('다시 열고 설치 창이 다시 준비돼도 닫은 뒤 14일 동안은 권하지 않는다', async () => {
      await page.reload();
      await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
      await offerInstall();
      await expect(page.locator('#push-prompt')).toBeVisible();
      await expect(page.locator('#install-banner')).toHaveCount(0);
    });
  });

  test('새 버전을 배포하면 열려 있던 화면에 "새 버전이 있어요"가 뜨고, 새로고침하면 새 버전이 화면을 맡는다', async ({ page, context }) => {
    // 지난 배포 = 지금 서버의 sw.js 와 바이트가 다른 서비스워커. 처음 등록할 때만 다른 바이트를 내주고, 그 뒤로는 서버 그대로(= 새 배포)
    await context.route('**/sw.js', async route => {
      const response = await route.fetch();
      await route.fulfill({ response, body: (await response.text()) + '\n// previous build' });
    });
    await openApp(page);
    await workerScript(page);
    await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);
    await context.unroute('**/sw.js');
    await expect(page.locator('#update-banner')).toHaveCount(0);

    await test.step('새 버전을 확인하면 설치를 마친 새 서비스워커가 기다리고, 화면이 새로고침을 권한다', async () => {
      await page.evaluate(async () => (await navigator.serviceWorker.getRegistration())?.update());
      await expect(page.locator('#update-banner')).toBeVisible();
      await expect(page.locator('#update-banner')).toContainText('새 버전이 있어요');
      expect(await page.evaluate(async () => !!(await navigator.serviceWorker.getRegistration())?.waiting)).toBe(true);
    });

    await test.step('새로고침을 누르면 새 버전으로 넘어가 다시 열리고, 기다리는 버전이 남지 않는다', async () => {
      const reloaded = page.waitForEvent('load');
      await page.click('#update-reload');
      await reloaded;
      await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
      await expect(page.locator('#update-banner')).toHaveCount(0);
      const state = await page.evaluate(async () => {
        const registration = await navigator.serviceWorker.getRegistration();
        return { controlled: !!navigator.serviceWorker.controller, waiting: !!registration?.waiting };
      });
      expect(state).toEqual({ controlled: true, waiting: false });
    });
  });
});

test.describe('첫 체크인 뒤에 묻고, 예라고 한 브라우저에만 알림이 간다', () => {
  test('예를 누르면 그때 브라우저 권한을 묻고 이 브라우저를 등록하며, 이번 주 미스터리 알림이 실제로 발송돼 서비스워커가 알림을 띄운다', async ({ page, context, request, dev, baseURL }) => {
    const box = 'phone-mystery';
    await request.delete('/dev/push/inbox');
    await context.grantPermissions(['notifications'], { origin: baseURL });
    await useFakePushService(context, box);
    const events = collectEvents(page);
    await openApp(page);
    await workerScript(page);

    await test.step('첫 체크인 전에는 묻지 않는다', async () => {
      await expect(page.locator('#push-prompt')).toHaveCount(0);
    });

    await test.step('지역을 칠하면 "알림 받을래요?"가 나타난다', async () => {
      await checkInOnScreen(page, GAPYEONG);
      await expect(page.locator('#push-prompt[data-step="ask"]')).toBeVisible();
      await expect(page.locator('#push-prompt')).toContainText('알림 받을래요?');
    });

    await test.step('예를 누르면 구독해 서버에 이 브라우저를 등록한다', async () => {
      const registered = page.waitForResponse(response => new URL(response.url()).pathname === '/push/subscriptions' && response.request().method() === 'POST');
      await page.click('#push-yes');
      const response = await registered;
      expect(response.status()).toBe(200);
      expect(await response.json()).toMatchObject({ created: true, devices: 1 });
      await expect(page.locator('#push-prompt')).toContainText('알림을 켰어요');
      const token = await dev.explorerOf(page);
      expect(await preferences(request, token)).toMatchObject({ mystery: true, streak: true, season: true, devices: 1, dailyLimit: 1 });
    });

    await test.step('질문을 보였고 허용했다고 남긴다', async () => {
      await expect.poll(() => events.filter(event => event.name === 'push_prompt').map(event => event.props?.result), { timeout: 15000 })
        .toEqual(['shown', 'granted']);
    });

    let message: { title: string; body: string; url: string; kind: string; tag: string } | undefined;
    await test.step('미스터리 알림을 지금 보내면 서버가 암호화·서명해 이 브라우저의 푸시 서비스로 보낸다(지역 이름은 싣지 않는다)', async () => {
      const result = await sendNow(request, 'mystery');
      expect(result.runs[0]?.decisions.PLANNED).toBe(1);
      expect(result.dispatch.outcomes.SENT).toBe(1);
      const received = await inbox(request, box);
      expect(received).toHaveLength(1);
      expect(received[0]).toMatchObject({ status: 201, urgency: 'normal', topic: 'mystery', contentEncoding: 'aes128gcm', vapid: true });
      const token = await dev.explorerOf(page);
      const deliveries = await (await request.get('/dev/push/deliveries', { headers: { [EXPLORER_HEADER]: token } })).json();
      const sent = deliveries.find((delivery: { kind: string; status: string }) => delivery.kind === 'mystery' && delivery.status === 'SENT');
      expect(sent?.url).toBe('/?from=push&push=mystery#map');
      expect(sent?.title).toContain('미스터리');
      message = { title: sent.title, body: sent.body, url: sent.url, kind: 'mystery', tag: `mystery-${sent.period}` };
    });

    await test.step('같은 본문의 푸시를 받은 서비스워커가 알림을 띄운다(누르면 열 주소를 싣고)', async () => {
      if (!message) throw new Error('발송 기록이 없다');
      await deliverToWorker(context, page, message);
      await expect.poll(async () => (await shownNotifications(page)).map(notification => notification.title)).toContain(message?.title);
      const [shown] = (await shownNotifications(page)).filter(notification => notification.title === message?.title);
      expect(shown?.tag).toBe(message?.tag);
      expect(shown?.data).toEqual({ url: new URL(message?.url ?? '/', page.url()).href, kind: 'mystery' });
    });

    await test.step('같은 날 다른 알림은 보내지 않는다(하루 최대 1개)', async () => {
      const result = await sendNow(request, 'season');
      expect(result.dispatch.outcomes.SENT ?? 0).toBe(0);
      expect(await inbox(request, box)).toHaveLength(1);
    });

    await test.step('다시 열어도 다시 묻지 않고, 같은 구독을 조용히 다시 보낸다', async () => {
      const resent = page.waitForResponse(response => new URL(response.url()).pathname === '/push/subscriptions');
      await page.reload();
      await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
      expect(await (await resent).json()).toMatchObject({ created: false, devices: 1 });
      await expect(page.locator('#push-prompt')).toHaveCount(0);
    });
  });

  test('알림을 눌러 열면 그 화면으로 열리고, 알림으로 들어왔다고 센다', async ({ page }) => {
    const events = collectEvents(page);
    await openApp(page, '/?from=push&push=season#sets');
    await expect(page.locator('#tab-sets')).toBeVisible();
    // 새로고침·즐겨찾기에 표시가 남지 않게 주소에서 지운다
    await expect.poll(() => new URL(page.url()).search).toBe('');
    expect(new URL(page.url()).hash).toBe('#sets');
    await expect.poll(() => events.filter(event => event.name === 'app_open' || event.name === 'push_open'), { timeout: 15000 }).toEqual([
      expect.objectContaining({ name: 'app_open', props: { entry: 'push' } }),
      expect.objectContaining({ name: 'push_open', props: { kind: 'season' } }),
    ]);
  });

  test('브라우저 권한을 거절하면 조용히 안내만 하고 다시 묻지 않는다', async ({ page, context }) => {
    await useFakePushService(context, 'phone-denied');
    const events = collectEvents(page);
    await openApp(page);
    await workerScript(page);
    // 헤드리스 크로미움은 권한을 주지 않은 알림 요청을 거절한다
    await page.evaluate(() => {
      Notification.requestPermission = async () => 'denied' as const;
    });
    await checkInOnScreen(page, GAPYEONG);
    await page.click('#push-yes');
    await expect(page.locator('#push-prompt')).toContainText('알림을 허용하지 않았어요');
    // 오류 토스트로 다그치지 않는다(질문 카드 안에서만 안내)
    await expect(page.locator('.toast', { hasText: '알림' })).toHaveCount(0);
    await expect.poll(() => events.filter(event => event.name === 'push_prompt').map(event => event.props?.result), { timeout: 15000 })
      .toEqual(['shown', 'denied']);
    await page.click('#push-prompt-close');
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    await expect(page.locator('#push-prompt')).toHaveCount(0);
  });

  test('"나중에"를 누르면 질문이 사라지고 다시 열어도 묻지 않는다', async ({ page }) => {
    await openApp(page);
    await workerScript(page);
    await checkInOnScreen(page, GAPYEONG);
    await page.click('#push-later');
    await expect(page.locator('#push-prompt')).toHaveCount(0);
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    await expect(page.locator('#push-prompt')).toHaveCount(0);
  });
});

test.describe('프로필 탭에서 알림을 고른다', () => {
  test('종류를 끄면 그 알림은 오지 않고, 이 기기 알림을 모두 끄면 구독이 풀려 아무 알림도 오지 않는다', async ({ page, context, request, dev, baseURL }) => {
    const box = 'phone-settings';
    await request.delete('/dev/push/inbox');
    await context.grantPermissions(['notifications'], { origin: baseURL });
    await useFakePushService(context, box);
    await openApp(page);
    await workerScript(page);
    await checkInOnScreen(page, GAPYEONG);
    await page.click('#push-yes');
    await expect(page.locator('#push-prompt')).toContainText('알림을 켰어요');
    const token = await dev.explorerOf(page);

    await test.step('프로필 탭에 이 기기 상태와 종류 세 가지, 서버가 정한 규칙이 보인다', async () => {
      await page.click('#tabs [data-tab="profile"]');
      await expect(page.locator('#push-settings')).toHaveAttribute('data-status', 'on');
      await expect(page.locator('#push-kinds input[type="checkbox"]')).toHaveCount(3);
      await expect(page.locator('#push-rules')).toContainText('22:00~08:00');
      await expect(page.locator('#push-rules')).toContainText('하루에 1개');
      await expect(page.locator('#push-rules')).toContainText('알림 받는 기기 1대');
    });

    await test.step('미스터리 알림을 끄면 서버 설정이 바뀌고, 지금 보내도 오지 않는다', async () => {
      const saved = page.waitForResponse(response => new URL(response.url()).pathname === '/push/preferences' && response.request().method() === 'PUT');
      await page.locator('input[data-kind="mystery"]').uncheck();
      expect(await (await saved).json()).toMatchObject({ mystery: false, streak: true, season: true });
      expect(await preferences(request, token)).toMatchObject({ mystery: false, devices: 1 });
      const result = await sendNow(request, 'mystery');
      // 서버는 그 종류를 끈 사람을 받을 사람 목록에서부터 뺀다 — 계획도 발송도 없다
      expect(result.runs[0]?.decisions.PLANNED ?? 0).toBe(0);
      expect(result.dispatch.outcomes.SENT ?? 0).toBe(0);
      expect(await inbox(request, box)).toHaveLength(0);
    });

    await test.step('다시 켠 뒤 "이 기기 알림 모두 끄기"를 누르면 구독이 풀리고, 보내도 받을 기기가 없다', async () => {
      const saved = page.waitForResponse(response => new URL(response.url()).pathname === '/push/preferences' && response.request().method() === 'PUT');
      await page.locator('input[data-kind="mystery"]').check();
      await saved;
      const removed = page.waitForResponse(response => new URL(response.url()).pathname === '/push/subscriptions' && response.request().method() === 'DELETE');
      await page.click('#push-off');
      expect((await removed).status()).toBe(204);
      await expect(page.locator('#push-settings')).toHaveAttribute('data-status', 'off');
      await expect(page.locator('#push-enable')).toBeVisible();
      await expect(page.locator('#push-rules')).toContainText('알림 받는 기기가 없어요');
      expect(await preferences(request, token)).toMatchObject({ devices: 0 });
      const result = await sendNow(request, 'mystery');
      // 기기가 없으면(= 동의 없음) 받을 사람이 아니다
      expect(result.runs[0]?.decisions.PLANNED ?? 0).toBe(0);
      expect(result.dispatch.outcomes.SENT ?? 0).toBe(0);
      expect(await inbox(request, box)).toHaveLength(0);
    });

    await test.step('"이 기기에서 알림 켜기"로 다시 켜면 다시 받는다', async () => {
      await page.click('#push-enable');
      await expect(page.locator('#push-settings')).toHaveAttribute('data-status', 'on');
      expect(await preferences(request, token)).toMatchObject({ devices: 1 });
      const result = await sendNow(request, 'mystery');
      expect(result.dispatch.outcomes.SENT).toBe(1);
      expect(await inbox(request, box)).toHaveLength(1);
    });
  });
});
