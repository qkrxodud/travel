import { test, expect } from '../fixtures';
import type { Page, Request } from '@playwright/test';

/**
 * 10단계 분석 — 화면은 익명 방문 ID 로 허용된 화면 이벤트만 모아 보내고(개인정보 없이), 운영자는 관리자 화면(/#/admin)에서
 * 서버가 센 지표를 본다. 관리자 토큰은 local 프로파일 값(application-local.yml).
 */

const ADMIN_TOKEN = 'local-admin-token';
const GAPYEONG = '31370';
const SECRET_MEMO = '비밀메모-잣';

type SentEvent = { name: string; props?: Record<string, unknown> };
type SentBody = { visitorId: string; events: SentEvent[] };

const region = (page: Page, code: string) => page.locator(`path.region[data-code="${code}"]`);

/** 화면이 POST /events 로 보낸 본문을 모은다 */
function collectEvents(page: Page) {
  const bodies: SentBody[] = [];
  const raw: string[] = [];
  page.on('request', (request: Request) => {
    if (request.method() === 'POST' && new URL(request.url()).pathname === '/events') {
      const text = request.postData() ?? '';
      raw.push(text);
      bodies.push(JSON.parse(text) as SentBody);
    }
  });
  const names = () => bodies.flatMap(body => body.events.map(event => event.name));
  return { bodies, raw, names };
}

async function openAdmin(page: Page, token: string) {
  await page.goto('/#/admin');
  await page.fill('#admin-token', token);
  await page.click('#admin-open');
}

test.describe('운영자는 첫 방문부터 첫 체크인까지를 지표로 본다', () => {
  test('처음 온 방문자가 지역을 칠하면, 운영자는 관리자 화면의 오늘 퍼널에서 그 방문과 첫 체크인을 바로 본다', async ({ page, context, dev }) => {
    const sent = collectEvents(page);

    await test.step('첫 화면을 열고 체크인 모달을 한 번 닫았다가 다시 열어 메모와 함께 저장한다', async () => {
      await page.goto('/');
      await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
      await region(page, GAPYEONG).dispatchEvent('click');
      await expect(page.locator('#checkin')).toBeVisible();
      await page.click('#ci-cancel');
      await expect(page.locator('#checkin')).toBeHidden();
      await region(page, GAPYEONG).dispatchEvent('click');
      await expect(page.locator('#checkin')).toBeVisible();
      await page.fill('#ci-memo', SECRET_MEMO);
      const delivered = page.waitForResponse(response =>
        new URL(response.url()).pathname === '/events' && (response.request().postData() ?? '').includes('checkin_save'), { timeout: 15000 });
      await page.click('#ci-save');
      await expect(page.locator('#checkin')).toBeHidden();
      // 화면은 이벤트를 모았다가 몇 초 뒤 한 번에 보낸다
      expect((await delivered).status()).toBe(202);
    });

    await test.step('보낸 것은 허용된 화면 이벤트뿐이고, 메모·탐험가 토큰·id 는 싣지 않는다', async () => {
      expect(sent.names()).toEqual(expect.arrayContaining(['app_open', 'tab_view', 'checkin_open', 'checkin_cancel', 'checkin_save']));
      expect(sent.names().filter(name => name === 'checkin_open')).toHaveLength(2);
      const open = sent.bodies.flatMap(body => body.events).find(event => event.name === 'app_open');
      expect(open?.props).toEqual({ entry: 'direct' });
      const visitorId = await page.evaluate(() => localStorage.getItem('territory-visitor-id'));
      expect(sent.bodies.every(body => body.visitorId === visitorId)).toBe(true);
      const token = await dev.explorerOf(page);
      const explorerId = await dev.idOf(page);
      for (const text of sent.raw) {
        expect(text).not.toContain(SECRET_MEMO);
        expect(text).not.toContain(token);
        expect(text).not.toContain(explorerId);
      }
    });

    await test.step('관리자 화면은 탐험가를 만들거나 화면 이벤트를 보내지 않는다', async () => {
      const admin = await context.newPage();
      const adminRequests: string[] = [];
      admin.on('request', request => adminRequests.push(`${request.method()} ${new URL(request.url()).pathname}`));
      await openAdmin(admin, ADMIN_TOKEN);
      await expect(admin.locator('#admin-metrics')).toBeVisible();
      expect(adminRequests).not.toContain('POST /explorers');
      expect(adminRequests).not.toContain('POST /events');

      await test.step('오늘 줄: 첫 화면 1 → 첫 체크인 1, 새 방문 1 · 새 탐험가 1', async () => {
        const to = await admin.locator('#admin-metrics').getAttribute('data-to');
        const today = admin.locator(`#admin-funnel tr[data-cohort-day="${to}"]`);
        // 체크인 사실은 서버가 비동기(outbox)로 적는다 — 반영될 때까지 새로고침
        await expect(async () => {
          await admin.click('#admin-refresh');
          await expect(today.locator('[data-funnel="first-screen"]')).toHaveText('1', { timeout: 1000 });
          await expect(today.locator('[data-funnel="first-check-in"]')).toHaveText('1', { timeout: 1000 });
          await expect(admin.locator('#m-new-explorers')).toHaveAttribute('data-value', '1', { timeout: 1000 });
        }).toPass({ timeout: 20000 });
        await expect(today).toContainText('집계 중');
        await expect(admin.locator('#m-new-visitors')).toHaveAttribute('data-value', '1');
        await expect(admin.locator('#m-dau')).toHaveAttribute('data-value', '1');
        await expect(admin.locator('[data-feature="checkin_open"]')).toBeVisible();
      });
      await admin.close();
    });
  });

  test('공개 프로필의 앱 링크로 들어온 첫 화면은 프로필 유입으로 남고, 주소의 표시는 지워진다', async ({ page }) => {
    const sent = collectEvents(page);
    const delivered = page.waitForResponse(response =>
      new URL(response.url()).pathname === '/events' && (response.request().postData() ?? '').includes('app_open'), { timeout: 15000 });
    await page.goto('/?from=profile');
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    expect(new URL(page.url()).searchParams.has('from')).toBe(false);
    expect((await delivered).status()).toBe(202);
    const open = sent.bodies.flatMap(body => body.events).find(event => event.name === 'app_open');
    expect(open?.props).toEqual({ entry: 'profile' });
  });

  test('관리자 토큰이 없거나 틀리면 거절 이유를 알려 주고 지표는 보여 주지 않는다', async ({ page }) => {
    const requests: string[] = [];
    page.on('request', request => requests.push(`${request.method()} ${new URL(request.url()).pathname}`));

    await openAdmin(page, '');
    await expect(page.locator('#admin-error')).toHaveText('관리자 토큰을 입력해 주세요.');
    await expect(page.locator('#admin-metrics')).toHaveCount(0);

    await openAdmin(page, 'wrong-token');
    await expect(page.locator('#admin-error')).toContainText('관리자 토큰이 맞지 않아요');
    await expect(page.locator('#admin-metrics')).toHaveCount(0);

    // 토큰은 저장하지 않는다 — 새로고침하면 다시 물어본다
    await openAdmin(page, ADMIN_TOKEN);
    await expect(page.locator('#admin-metrics')).toBeVisible();
    const stored = await page.evaluate(() => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }) + document.cookie);
    expect(stored).not.toContain(ADMIN_TOKEN);
    await page.reload();
    await expect(page.locator('#admin-login')).toBeVisible();
    await expect(page.locator('#admin-metrics')).toHaveCount(0);
    expect(requests).not.toContain('POST /explorers');
  });

  test('개발용 시드로 쌓은 30일 이벤트로 추이·코호트 리텐션 표·기능 사용률·상위 오류를 본다', async ({ page, request }) => {
    const seeded = await request.post('/dev/analytics/seed?days=30&visitorsPerDay=40&seed=42');
    expect(seeded.ok(), await seeded.text()).toBeTruthy();

    await openAdmin(page, ADMIN_TOKEN);
    await expect(page.locator('#admin-metrics')).toBeVisible();
    await expect(page.locator('#admin-missing')).toHaveCount(0);
    // 시드는 원본 보관 기간(90일) 안이라 다시 셀 수 없는 날이 없다 — 빗금 띠·안내도 없다
    await expect(page.locator('#admin-expired')).toHaveCount(0);
    await expect(page.locator('#chart-active rect[data-gap]')).toHaveCount(0);

    await test.step('일별 추이 두 차트에 선이 그려지고 오늘 MAU 가 0 보다 크다', async () => {
      await expect(page.locator('#chart-active [data-series="dau"] path')).toHaveAttribute('d', /^M/);
      await expect(page.locator('#chart-new [data-series="new-visitors"] path')).toHaveAttribute('d', /^M/);
      expect(Number(await page.locator('#m-mau').getAttribute('data-value'))).toBeGreaterThan(0);
    });

    await test.step('코호트 리텐션 표: 지난 가입일마다 D1 이 채워지고, 아직 오지 않은 D30 은 빈칸이다', async () => {
      const rows = page.locator('#admin-retention tbody tr');
      expect(await rows.count()).toBeGreaterThan(20);
      const filledD1 = page.locator('#admin-retention td[data-retention="d1"]:not(.empty)');
      expect(await filledD1.count()).toBeGreaterThan(20);
      await expect(rows.first().locator('td[data-retention="d30"]')).toHaveClass(/empty/);
    });

    await test.step('지난 코호트의 퍼널은 확정, K 계수와 기능 사용률·상위 오류 코드가 보인다', async () => {
      await expect(page.locator('#admin-funnel tr[data-settled="true"]').first()).toBeVisible();
      await expect(page.locator('#admin-k dd[data-value]')).not.toHaveAttribute('data-value', '—');
      expect(await page.locator('#admin-features li[data-feature]').count()).toBeGreaterThan(3);
      expect(await page.locator('#admin-errors tr[data-error-code]').count()).toBeGreaterThan(0);
    });
  });
});
