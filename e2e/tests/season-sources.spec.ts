import { test, expect, EXPLORER_HEADER } from '../fixtures';
import type { APIRequestContext, Page } from '@playwright/test';

/**
 * 13s단계 — 계절 명소의 추천 근거. 회차 지역은 한국관광공사 TourAPI 축제·관광지를 근거로 고르거나(관리자가 후보를 보고 확정),
 * 근거가 없으면 검증 전 AI 추정 목록을 쓴다. 도감 "계절 한정" 섹션은 지역마다 무엇을 근거로 골랐는지와 출처를 알려 준다.
 *
 * 실제 TourAPI 키·실제 호출은 쓰지 않는다: E2E 서버는 가짜 키로 서버 자신의 가짜 TourAPI(/dev/tourapi, "[개발용]" 축제)를 부르게 떠 있다
 * (playwright.config.ts · compose.e2e.yaml). 회차가 열리는 때는 local 전용 서버 시계(/dev/clock)를 앞으로 밀어 만든다.
 */

const ADMIN_TOKEN = 'local-admin-token';
const FAKE_KEY = 'dev-fixture-key';
const SPRING_OPEN = '2027-03-25T12:00:00+09:00';

const tab = (page: Page, name: string) => page.locator(`#tabs [data-tab="${name}"]`).click();
const roundCard = (page: Page, roundId: string) => page.locator(`.lineup-round[data-round="${roundId}"]`);

type SeasonRegion = { code: string; provenance: string; evidence: { title: string; evidenceKind: string }[] };
type SeasonRound = { roundId: string; provenance: string; source: string | null; regions: SeasonRegion[] };

async function openGame(page: Page) {
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
}

async function openSeasonAdmin(page: Page) {
  await page.goto('/#/admin/seasons');
  await page.fill('#admin-token', ADMIN_TOKEN);
  await page.click('#admin-open');
  await expect(page.locator('#admin-seasons')).toBeVisible();
}

async function moveClockTo(request: APIRequestContext, to: string) {
  const res = await request.post('/dev/clock', { data: { to } });
  expect(res.ok(), `POST /dev/clock ${to} -> ${res.status()} ${await res.text()}`).toBeTruthy();
}

/** 서버 날짜가 가을 회차(10/1~11/30) 밖이면 가장 가까운 앞날의 10월 2일로 민다(시계는 앞으로만 간다) */
async function ensureAutumn(request: APIRequestContext) {
  const res = await request.get('/dev/clock');
  expect(res.ok()).toBeTruthy();
  const [year, month] = ((await res.json()).now as string).slice(0, 10).split('-').map(Number);
  if (month === 10 || month === 11) return;
  await moveClockTo(request, `${month === 12 ? year + 1 : year}-10-02T12:00:00+09:00`);
}

async function fakeTourApi(request: APIRequestContext, mode: 'ok' | 'no-attractions') {
  const res = await request.put('/dev/tourapi/mode', { data: { mode } });
  expect(res.ok(), `PUT /dev/tourapi/mode ${mode} -> ${res.status()}`).toBeTruthy();
}

async function openRoundOf(request: APIRequestContext, token: string): Promise<SeasonRound> {
  const res = await request.get('/seasons/current', { headers: { [EXPLORER_HEADER]: token } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).current[0];
}

test.beforeEach(async ({ request }) => {
  // 가짜 TourAPI 로 떠 있는 서버여야 한다(키 없는 서버를 재사용하면 근거 흐름을 확인할 수 없다)
  const res = await request.get('/admin/seasons', { headers: { 'X-Admin-Token': ADMIN_TOKEN } });
  expect(res.ok()).toBeTruthy();
  expect((await res.json()).tourApi.configured, 'E2E 서버는 가짜 TourAPI 키로 떠 있어야 한다(playwright.config.ts)').toBe(true);
  await fakeTourApi(request, 'ok');
});

test.afterEach(async ({ request }) => {
  await fakeTourApi(request, 'ok');
  await request.delete('/dev/clock');
});

test.describe('계절 명소의 추천 근거', () => {
  test('진행 중인 가을 회차는 검증 전 AI 추정 목록이라 지역마다 AI 추정이라고 알려 준다', async ({ page, dev, request }) => {
    await ensureAutumn(request);
    await openGame(page);
    const round = await openRoundOf(request, await dev.explorerOf(page));
    expect(round.provenance).toBe('ai-estimate');

    await tab(page, 'sets');
    await expect(page.locator('#season-sources-summary')).toHaveText('추천 근거: AI 추정(검증 전)');
    await page.click('#season-sources-summary');
    const lines = page.locator('#season-sources li');
    await expect(lines).toHaveCount(10);
    await expect(page.locator('#season-sources li[data-provenance="ai-estimate"]')).toHaveCount(10);
    await expect(lines.first()).toContainText('AI 추정');
    await expect(page.locator('#season-footnote')).toHaveText('출처: AI 추정은 공개 자료로 확인하기 전의 추천이에요.');
  });

  test('관리자가 다음 봄 회차 후보를 TourAPI 근거로 모아 확정하면, 회차가 열렸을 때 도감에 지역마다 축제 근거와 출처가 보인다', async ({ page, browser, dev, request }) => {
    const admin = await (await browser.newContext()).newPage();
    const adminResponses: string[] = [];
    admin.on('response', response => {
      if (!new URL(response.url()).pathname.startsWith('/admin/seasons')) return;
      response.text().then(body => adminResponses.push(body), () => undefined);
    });
    await openSeasonAdmin(admin);

    await test.step('TourAPI 연결 상태와 오늘 호출 수·하루 예산을 보여 주고, 진행 중인 회차는 바꿀 수 없게 잠가 둔다', async () => {
      await expect(admin.locator('#tourapi-status')).toHaveAttribute('data-configured', 'true');
      await expect(admin.locator('#tourapi-key-state')).toHaveText('키 연결됨');
      const calls = await admin.locator('#tourapi-usage').getAttribute('data-calls');
      await expect(admin.locator('#tourapi-usage')).toHaveText(`오늘 TourAPI 호출 ${calls} / 200회`);
      const autumn = roundCard(admin, 'autumn-2026');
      await expect(autumn).toHaveAttribute('data-locked', 'true');
      await expect(autumn).toHaveAttribute('data-confirmed-by', 'OPENING');
      await expect(autumn.locator('[data-action="refresh"]')).toBeDisabled();
      await expect(autumn.locator('[data-action="confirm"]')).toBeDisabled();
      // 아직 후보가 없는 회차는 확정할 수 없다
      await expect(roundCard(admin, 'spring-2027').locator('[data-action="confirm"]')).toBeDisabled();
    });

    await test.step('봄 회차를 새로 모으면 후보 열 곳이 순위대로 축제 근거와 함께 보인다', async () => {
      const spring = roundCard(admin, 'spring-2027');
      await spring.locator('[data-action="refresh"]').click();
      await expect(admin.locator('#lineup-detail')).toHaveAttribute('data-round', 'spring-2027');
      await expect(admin.locator('#lineup-candidate tbody tr')).toHaveCount(10);
      await expect(admin.locator('#lineup-candidate tbody tr[data-provenance="tourapi"]')).toHaveCount(10);
      await expect(admin.locator('#lineup-candidate tbody tr').first()).toContainText('축제 「[개발용]');
      await expect(admin.locator('#lineup-candidate tbody tr').first()).toContainText('한국관광공사 TourAPI');
      await expect(spring.locator('.lineup-candidate-summary')).toHaveAttribute('data-evidenced', '10');
      await expect(spring.locator('.lineup-shortage')).toHaveCount(0);
    });

    await test.step('후보를 확정하면 이 회차에 쓰는 목록이 관리자 확정 TourAPI 목록으로 바뀐다', async () => {
      const spring = roundCard(admin, 'spring-2027');
      await spring.locator('[data-action="confirm"]').click();
      await expect(spring).toHaveAttribute('data-confirmed-by', 'ADMIN');
      await expect(spring).toHaveAttribute('data-provenance', 'tourapi');
      await expect(admin.locator('#lineup-in-effect tbody tr[data-provenance="tourapi"]')).toHaveCount(10);
    });

    await test.step('관리자 토큰도 TourAPI 키도 화면·저장소·응답 어디에도 남지 않는다', async () => {
      const stored = await admin.evaluate(() => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }) + document.cookie);
      expect(stored).not.toContain(ADMIN_TOKEN);
      expect(await admin.content()).not.toContain(FAKE_KEY);
      expect(adminResponses.join('\n')).not.toContain(FAKE_KEY);
    });
    await admin.close();

    await test.step('봄 회차가 열리면 도감에 지역마다 축제 이름·기간과 한국관광공사 TourAPI 출처가 서버 순위대로 보인다', async () => {
      await moveClockTo(request, SPRING_OPEN);
      await openGame(page);
      const round = await openRoundOf(request, await dev.explorerOf(page));
      expect(round.roundId).toBe('spring-2027');
      expect(round.source).toBe('한국관광공사 TourAPI');

      await tab(page, 'sets');
      await expect(page.locator('#season-sources-summary')).toHaveText('추천 근거: 한국관광공사 TourAPI');
      await page.click('#season-sources-summary');
      const lines = page.locator('#season-sources li');
      await expect(lines).toHaveCount(10);
      const order = await lines.evaluateAll(items => items.map(item => item.getAttribute('data-source-region')));
      expect(order).toEqual(round.regions.map(region => region.code.replace('KR-', '')));
      const first = lines.first();
      await expect(first).toHaveAttribute('data-provenance', 'tourapi');
      await expect(first).toContainText('한국관광공사 TourAPI');
      await expect(first).toContainText(`축제 「${round.regions[0].evidence[0].title}」`);
      await expect(first).toContainText(/\d+\/\d+/);
      await expect(page.locator('#season-footnote')).toContainText('출처: 한국관광공사 TourAPI 축제·관광지 정보');
    });
  });

  test('근거가 모자란 가을 후보는 열 곳 미만이라 자동 확정하지 않는다고 경고하고, 보는 사이 봄 회차가 열리면 바꿀 수 없다고 알려 준다', async ({ page, request }) => {
    await openSeasonAdmin(page);

    await test.step('관광지 근거가 없는 날 캐시 없이 다시 모으면 축제가 있는 곳만 근거가 되고 부족하다고 경고한다', async () => {
      await fakeTourApi(request, 'no-attractions');
      const autumn = roundCard(page, 'autumn-2027');
      const before = Number(await page.locator('#tourapi-usage').getAttribute('data-calls'));
      await autumn.locator('[data-action="refresh-fresh"]').click();
      await expect(page.locator('#lineup-detail')).toHaveAttribute('data-round', 'autumn-2027');
      await expect(autumn.locator('.lineup-shortage')).toContainText('10곳 미만이라 자동 확정하지 않아요');
      await expect(page.locator('#lineup-detail-shortage')).toBeVisible();
      await expect(page.locator('#lineup-candidate tbody tr[data-provenance="ai-estimate"]').first()).toContainText('AI 추정(검증 전)');
      // 캐시를 건너뛰었으니 오늘 호출 수가 늘었다
      await expect.poll(async () => Number(await page.locator('#tourapi-usage').getAttribute('data-calls'))).toBeGreaterThan(before);
    });

    await test.step('보는 사이 봄 회차가 열리면 새로 모으기를 서버가 거절하고, 진행 중인 목록은 고정된다고 알려 준다', async () => {
      await moveClockTo(request, SPRING_OPEN);
      const spring = roundCard(page, 'spring-2027');
      await expect(spring.locator('[data-action="refresh"]')).toBeEnabled();
      await spring.locator('[data-action="refresh"]').click();
      const error = spring.locator('.lineup-error');
      await expect(error).toHaveAttribute('data-code', 'SEASON_ROUND_LOCKED');
      await expect(error).toContainText('이미 열렸거나 지난 회차라 지역 목록을 바꿀 수 없어요');
    });

    await test.step('목록을 다시 읽으면 열린 봄 회차는 잠겨 있다', async () => {
      await page.click('#admin-refresh');
      await expect(roundCard(page, 'spring-2027')).toHaveAttribute('data-locked', 'true');
      await expect(roundCard(page, 'spring-2027').locator('[data-action="refresh"]')).toBeDisabled();
    });
  });
});
