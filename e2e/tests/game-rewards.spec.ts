import { test, expect, EXPLORER_HEADER } from '../fixtures';
import type { APIRequestContext, Browser, Page } from '@playwright/test';

/**
 * 8단계(게임 요소 1순위) — 이번 주 미스터리 지역 · 시·도 정복 · 연속 탐험 마일스톤 · 스트릭 보호권.
 * 주·달이 바뀌어야 보이는 규칙은 local 전용 서버 시계(/dev/clock)를 앞으로 밀어 확인한다(브라우저 시계는 그대로).
 * 보상은 outbox 릴레이(비동기)로 반영되므로 기대값은 기다린다. 서버 시계는 테스트가 끝나면 되돌린다(reset 도 되돌린다).
 */

const SEJONG = '29010'; // 세종특별자치시 — 시·도 안 지역이 한 곳뿐이라 칠하면 바로 정복
const JONGNO = '11010';
const JUNG = '11020';
const YONGSAN = '11030';
const OKCHEON = 'KR-33330'; // 충북 옥천군(희귀) — 이 스펙에서 이번 주 미스터리 지역으로 고정한다
const NAMWON = 'KR-35050';  // 전북 남원시 — 지리산 둘레 세트
const GURYE = 'KR-36330';   // 전남 구례군(희귀) — 지리산 둘레 세트
const LATE = { timeout: 10_000 };

const region = (page: Page, code: string) => page.locator(`path.region[data-code="${code}"]`);
const toast = (page: Page, text: string | RegExp) => page.locator('.toast', { hasText: text });
const tab = (page: Page, name: string) => page.locator(`#tabs [data-tab="${name}"]`).click();

type MysteryWeek = { weekStart: string; region: { code: string; name: string; provinceName: string; rarity: string }; bonusXp: number; received: boolean };

async function open(page: Page) {
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
  await expect(page.locator('html')).toHaveAttribute('data-progress', /\d+/);
}

async function paint(page: Page, code: string) {
  await region(page, code).dispatchEvent('click');
  await expect(page.locator('#checkin')).toBeVisible();
  await page.click('#ci-save');
  await expect(page.locator('#checkin')).toBeHidden();
  await expect(region(page, code)).toHaveClass(/\bon\b/);
}

/** 서버 시계(서울 시각)의 지금 달(yyyy-MM) */
async function serverMonth(request: APIRequestContext): Promise<string> {
  const res = await request.get('/dev/clock');
  expect(res.ok()).toBeTruthy();
  return ((await res.json()).now as string).slice(0, 7);
}

/** start(yyyy-MM)에서 n달 뒤 그 달 10일 낮으로 서버 시계를 민다(31일씩 밀면 2월을 건너뛸 수 있어 날짜로 맞춘다) */
async function moveClockToMonth(request: APIRequestContext, start: string, months: number) {
  const [year, month] = start.split('-').map(Number);
  const target = new Date(Date.UTC(year, month - 1 + months, 1));
  const to = `${target.getUTCFullYear()}-${String(target.getUTCMonth() + 1).padStart(2, '0')}-10T12:00:00+09:00`;
  const res = await request.post('/dev/clock', { data: { to } });
  expect(res.ok(), `POST /dev/clock ${to} -> ${res.status()} ${await res.text()}`).toBeTruthy();
}

async function moveClockDays(request: APIRequestContext, days: number) {
  const res = await request.post('/dev/clock', { data: { days } });
  expect(res.ok(), `POST /dev/clock +${days}d -> ${res.status()}`).toBeTruthy();
}

async function mysteryOf(request: APIRequestContext, headers: Record<string, string> = {}): Promise<MysteryWeek> {
  const res = await request.get('/mystery/this-week', { headers });
  expect(res.ok(), `GET /mystery/this-week -> ${res.status()}`).toBeTruthy();
  return res.json();
}

const clientCode = (serverCode: string) => serverCode.replace(/^KR-/, '');

test.afterEach(async ({ request }) => {
  await request.delete('/dev/clock');
});

test.describe('게임 보상', () => {
  test('이번 주 미스터리 지역을 찾고, 세종을 정복하고, 석 달을 이어 칠해 마일스톤과 보호권으로 스트릭을 지킨다', async ({ page, dev, request }) => {
    const start = await serverMonth(request);
    await dev.pinMystery(OKCHEON);
    await open(page);
    const me = await dev.explorerOf(page);
    const week = await mysteryOf(request, { [EXPLORER_HEADER]: me });
    expect(week.region.name).toBe('옥천군');
    const mysteryCode = clientCode(week.region.code);

    await test.step('지도에 이번 주 미스터리 마커가 서고, 카드는 지역 이름 대신 남은 기간과 보너스를 보여 준다', async () => {
      await expect(page.locator(`g.mystery-mark[data-mystery="${mysteryCode}"]`)).toBeAttached();
      await expect(page.locator('#mystery-card')).toHaveAttribute('data-received', 'false');
      await expect(page.locator('#mystery-name')).toContainText('어딘가의');
      await expect(page.locator('#mystery-left')).toContainText('남음');
      await expect(page.locator('#mystery-bonus')).toHaveText(`+${week.bonusXp} XP`);
    });

    await test.step('카드를 누르면 지도에서 그 지역을 보여 주고 이름을 알려 준다', async () => {
      await page.click('#mystery-show');
      await expect(page.locator('#mystery-name')).toHaveText(`${week.region.provinceName} ${week.region.name}`);
      await expect(region(page, mysteryCode)).toHaveClass(/\bhl\b/);
    });

    await test.step('미스터리 지역을 칠하려 하면 예상 보상에 미스터리 보너스가 붙고, 칠하면 보너스를 받아 카드와 마커에 받음 표시가 생긴다', async () => {
      await region(page, mysteryCode).dispatchEvent('click');
      await expect(page.locator('#checkin')).toBeVisible();
      await expect(page.locator('#ci-gains [data-source="MYSTERY_BONUS"]')).toContainText(`+${week.bonusXp}`);
      await expect(page.locator('#ci-gains [data-source="MYSTERY_BONUS"]')).toContainText('이번 주 미스터리 보너스');
      await page.click('#ci-save');
      await expect(page.locator('#checkin')).toBeHidden();
      await expect(toast(page, '이번 주 미스터리 지역을 찾았어요')).toBeVisible(LATE);
      await expect(page.locator('#mystery-card')).toHaveAttribute('data-received', 'true');
      await expect(page.locator('#mystery-bonus')).toHaveText('받음 ✓');
      await expect(page.locator(`g.mystery-mark.received[data-mystery="${mysteryCode}"]`)).toBeAttached();
    });

    await test.step('프로필에는 미스터리 탐험가 뱃지가 생긴다', async () => {
      await tab(page, 'profile');
      await expect(page.locator('.badge[data-badge="mystery1"]')).toHaveClass(/\bgot\b/, LATE);
      await tab(page, 'map');
    });

    await test.step('세종 한 곳을 칠하면 세종 정복을 알리고, 지도 테두리가 반짝이며 정복률 목록에 왕관이 씌워진다', async () => {
      await paint(page, SEJONG);
      await expect(toast(page, '세종 정복!')).toBeVisible(LATE);
      await expect(toast(page, '세종 정복!')).toContainText('+300 XP');
      await expect(page.locator('g.conquest[data-province="세종"]')).toBeAttached();
      await expect(page.locator('g.conquest.flash[data-province="세종"]')).toBeAttached();
      await expect(page.locator('#prov button[data-prov="세종"]')).toHaveAttribute('data-crowned', 'true');
      await expect(page.locator('#prov button[data-prov="세종"] .crown')).toHaveText('👑');
      await expect(page.locator('#prov button[data-prov="서울"]')).toHaveAttribute('data-crowned', 'false');
    });

    await test.step('정복 대표 장식은 가방에 들어오고 어디서 받았는지 보인다', async () => {
      await tab(page, 'bag');
      const decoration = page.locator('#bag-inv [data-equip="conquest:KR-29"]');
      await expect(decoration).toHaveAttribute('data-origin', '세종 정복 보상', LATE);
      await expect(decoration.locator('.ach')).toHaveText('👑');
      await tab(page, 'map');
    });

    await test.step('새로고침해도 정복 테두리와 왕관은 그대로고, 이번 주 보너스를 받은 미스터리 카드는 누르지 않아도 지역 이름을 보여 준다', async () => {
      await open(page);
      await expect(page.locator('#mystery-name')).toHaveText(`${week.region.provinceName} ${week.region.name}`);
      await expect(page.locator('g.conquest[data-province="세종"]')).toBeAttached();
      await expect(page.locator('#prov button[data-prov="세종"]')).toHaveAttribute('data-crowned', 'true');
    });

    await test.step('보호권은 처음에 하나도 없어 흐리게 보이고, 얻는 방법을 알려 준다', async () => {
      await expect(page.locator('#s-freeze')).toHaveText('🧊×0');
      await expect(page.locator('#s-freeze')).toHaveClass(/\bdim\b/);
      await expect(page.locator('#s-freeze')).toHaveAttribute('title', /월간 퀘스트 보상 4개를 모두 받으면 1개\(최대 2개\)/);
      await tab(page, 'quests');
      await expect(page.locator('#freeze-progress')).toContainText('이번 달 보상 0/4 받음');
      await expect(page.locator('#milestone-next')).toHaveText('다음 마일스톤 3개월까지 2개월 남았어요.');
      await expect(page.locator('.milestone[data-milestone="3"]')).toHaveAttribute('data-reached', 'false');
    });

    await test.step('이번 달 월간 퀘스트 네 개를 모두 달성해 보상을 다 받으면 보호권 1개를 얻는다', async () => {
      // 미스터리(충북 첫 발·희귀)·세종에 지리산 둘레 세트 두 곳을 더하면 새 영토 3곳·시골·처음 가는 시·도·도감 채우기를 모두 달성한다
      const today = new Date().toISOString().slice(0, 10);
      await dev.checkIn(me, NAMWON, today);
      await dev.checkIn(me, GURYE, today);
      // 화면 밖(API)에서 칠한 것은 진행에 늦게 반영되므로, 서버가 네 퀘스트를 모두 달성으로 셀 때까지 기다린 뒤 화면을 연다
      await expect.poll(async () => (await (await request.get('/quests', { headers: { [EXPLORER_HEADER]: me } })).json()).monthlyDone, LATE).toBe(4);
      await open(page);
      await tab(page, 'quests');
      await expect(page.locator('#q-month')).toContainText('4/4 완료', LATE);
      for (const quest of ['m3', 'mgun', 'mprov', 'mset']) {
        await page.locator(`[data-claim="${quest}"]`).click();
        await expect(page.locator(`.quest[data-quest="${quest}"] .st`)).toHaveText('받음');
      }
      await expect(page.locator('#freeze-progress')).toContainText('이번 달 보상 4/4 받음 — 이번 달 보호권 1개를 받았어요');
      await expect(page.locator('#s-freeze')).toHaveText('🧊×1', LATE);
      await expect(page.locator('#s-freeze')).not.toHaveClass(/\bdim\b/);
      await tab(page, 'map');
    });

    await test.step('한 주가 지나면 새 주의 미스터리 카드가 열리고 아직 받지 않은 상태다', async () => {
      await moveClockDays(request, 7);
      await open(page);
      await expect(page.locator('#mystery-card')).not.toHaveAttribute('data-week', week.weekStart);
      await expect(page.locator('#mystery-card')).toHaveAttribute('data-received', 'false');
      await expect(page.locator('#mystery-name')).toContainText('어딘가의');
      await expect(page.locator('#mystery-bonus')).toHaveText(`+${week.bonusXp} XP`);
    });

    await test.step('다음 달과 그다음 달에도 칠하면 3개월 연속 탐험 마일스톤을 달성해 칭호·보호권을 받는다', async () => {
      await moveClockToMonth(request, start, 1);
      await dev.checkIn(me, 'KR-' + JONGNO, new Date().toISOString().slice(0, 10));
      await moveClockToMonth(request, start, 2);
      await open(page);
      await paint(page, JUNG);
      await expect(toast(page, '연속 탐험 3개월 달성')).toBeVisible(LATE);
      await expect(page.locator('#s-streak')).toHaveText('3', LATE);
      await expect(page.locator('#s-freeze')).toHaveText('🧊×2'); // 월간 퀘스트 몫 1 + 마일스톤 1(최대 2)
      await tab(page, 'quests');
      const three = page.locator('.milestone[data-milestone="3"]');
      await expect(three).toHaveAttribute('data-reached', 'true');
      await expect(three.locator('.st')).toHaveText('✓ 달성');
      await expect(three).toContainText('칭호 「꾸준한 탐험가」');
      await expect(page.locator('#milestone-next')).toHaveText('다음 마일스톤 6개월까지 3개월 남았어요.');
    });

    await test.step('한 달을 건너뛰어도 이번 달에 칠하면 보호권 1개로 스트릭을 지켜 4개월째가 된다', async () => {
      await moveClockToMonth(request, start, 4);
      await open(page);
      await expect(page.locator('#s-streak')).toHaveText('3');
      await tab(page, 'quests');
      await expect(page.locator('#streak-txt')).toContainText('보호권 1개로 이어져요');
      await tab(page, 'map');
      await paint(page, YONGSAN);
      await expect(toast(page, '보호권 1개로 스트릭을 지켰어요')).toBeVisible(LATE);
      await expect(page.locator('#s-streak')).toHaveText('4', LATE);
      await expect(page.locator('#s-freeze')).toHaveText('🧊×1');
      await tab(page, 'quests');
      await expect(page.locator('#streak span.frz')).toHaveCount(1);
      await expect(page.locator('#streak span.on')).toHaveCount(4);
    });
  });

  test('친구의 시·도 정복·미스터리 발견·연속 탐험 달성이 친구 소식에 보인다', async ({ browser, dev }) => {
    const kim = await user(browser, dev, 'kim.game+e2e@example.com');
    const lee = await user(browser, dev, 'lee.game+e2e@example.com');
    const start = await serverMonth(lee.page.request);

    await test.step('김과 이가 서로 팔로우하고, 이는 프로필을 친구 공개로 둔다', async () => {
      // 아직 친구가 아닌 사람의 숨은 프로필은 없는 사람과 같은 답(404)을 받지만 팔로우는 남는다(소셜 규칙)
      const follow = await kim.page.request.post(`/friends/${lee.handle}`, { headers: { 'X-XSRF-TOKEN': await xsrf(kim.page) } });
      expect([200, 404]).toContain(follow.status());
      await send(lee.page, 'post', `/friends/${kim.handle}`);
      await send(lee.page, 'put', '/me/privacy', { visibility: 'FRIENDS' });
    });

    const week = await test.step('이가 이번 주 미스터리 지역과 세종을 칠하고, 석 달 이어 칠한다', async () => {
      const found = await mysteryOf(lee.page.request);
      await checkIn(lee.page, found.region.code);
      await checkIn(lee.page, 'KR-' + SEJONG);
      await moveClockToMonth(lee.page.request, start, 1);
      await checkIn(lee.page, 'KR-' + JONGNO);
      await moveClockToMonth(lee.page.request, start, 2);
      await checkIn(lee.page, 'KR-' + JUNG);
      return found;
    });

    await test.step('김의 친구 소식에 세 가지 게임 소식이 이의 이름으로 보인다', async () => {
      await expect.poll(async () => (await (await kim.page.request.get('/feed')).json()).items.map((item: { kind: string }) => item.kind), LATE)
        .toEqual(expect.arrayContaining(['STREAK_MILESTONE', 'PROVINCE_CONQUERED', 'MYSTERY_FOUND']));
      await kim.page.reload();
      await expect(kim.page.locator('html')).toHaveAttribute('data-territory', 'ready');
      await tab(kim.page, 'rank');
      await expect(kim.page.locator('#feed li[data-kind="PROVINCE_CONQUERED"]')).toContainText(`@${lee.handle}님이 세종을 정복했어요`);
      await expect(kim.page.locator('#feed li[data-kind="STREAK_MILESTONE"]')).toContainText(`@${lee.handle}님이 3개월 연속 탐험을 달성했어요`);
      await expect(kim.page.locator('#feed li[data-kind="MYSTERY_FOUND"]')).toContainText(`@${lee.handle}님이 이번 주 미스터리 지역 ${week.region.name}`);
      await expect(kim.page.locator('#feed li[data-kind="MYSTERY_FOUND"]')).toContainText('찾았어요');
    });

    await kim.context.close();
    await lee.context.close();
  });
});

/** 세션 요청의 변경 메서드는 CSRF 헤더(쿠키 XSRF-TOKEN 값)가 필요하다. 화면을 한 번 연 뒤에 부른다. */
async function xsrf(page: Page) {
  return (await page.context().cookies()).find(cookie => cookie.name === 'XSRF-TOKEN')?.value ?? '';
}

async function send(page: Page, method: 'post' | 'put', url: string, data?: unknown) {
  const res = await page.request[method](url, { headers: { 'X-XSRF-TOKEN': await xsrf(page) }, data });
  expect(res.ok(), `${method.toUpperCase()} ${url} -> ${res.status()} ${await res.text()}`).toBeTruthy();
}

async function checkIn(page: Page, regionCode: string) {
  await send(page, 'post', '/visits', { regionCode, visitDate: new Date().toISOString().slice(0, 10) });
}

async function user(browser: Browser, dev: { login: (target: Page, opts: { email: string }) => Promise<{ handle: string }> }, email: string) {
  const context = await browser.newContext();
  const page = await context.newPage();
  const login = await dev.login(page, { email });
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
  await expect(page.locator('html')).toHaveAttribute('data-logged-in', 'true');
  return { context, page, handle: login.handle };
}
