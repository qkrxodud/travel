import { test, expect, EXPLORER_HEADER } from '../fixtures';
import type { APIRequestContext, Page } from '@playwright/test';

/**
 * 9단계(게임 요소 2순위) — 계절 한정 테마 · 재방문 도장 · 가고 싶은 곳.
 * 기간·해 넘김은 local 전용 서버 시계(/dev/clock)를 앞으로 밀어 확인한다(브라우저 시계는 그대로). 진행·가방·다녀옴은 outbox 릴레이(비동기)로
 * 반영되므로 기대값은 기다린다. 서버 시계는 테스트가 끝나면 되돌린다(reset 도 되돌린다). 미스터리 지역은 fixture 가 기장군으로 고정해 둔다.
 */

/** 가을 "단풍 명소" 회차 지역(서버 카탈로그 seasons.json 과 같은 순서) */
const AUTUMN = ['32060', '35040', '32340', '37330', '36450', '33320', '38400', '11090', '31370', '35310'];
const SOKCHO = '32060'; // 강원 속초시 — 가을 회차 첫 지역, 해가 바뀐 뒤 다시 다녀온다
const JONGNO = '11010';
const JUNG = '11020';
const YONGSAN = '11030';
const LATE = { timeout: 10_000 };

const region = (page: Page, code: string) => page.locator(`path.region[data-code="${code}"]`);
const toast = (page: Page, text: string | RegExp) => page.locator('.toast', { hasText: text });
const tab = (page: Page, name: string) => page.locator(`#tabs [data-tab="${name}"]`).click();

type SeasonRound = { roundId: string; name: string; have: number; total: number; completed: boolean; year: number; backgroundItemId: string };
type Seasons = { current: SeasonRound[]; history: SeasonRound[]; next: { name: string } | null };

async function open(page: Page) {
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
  await expect(page.locator('html')).toHaveAttribute('data-progress', /\d+/);
}

async function paint(page: Page, code: string) {
  await page.click('#t-paint');
  await region(page, code).dispatchEvent('click');
  await expect(page.locator('#checkin')).toBeVisible();
  await page.click('#ci-save');
  await expect(page.locator('#checkin')).toBeHidden();
  await expect(region(page, code)).toHaveClass(/\bon\b/);
}

/** 기록 보기 모드에서 지역을 고른다(칠한 곳을 눌러도 지우지 않는다) */
async function pick(page: Page, code: string) {
  await page.click('#t-detail');
  await region(page, code).dispatchEvent('click');
  await expect(page.locator('#d-body .name')).toBeVisible();
}

/** 서버 시계(서울 시각)의 지금 "yyyy-MM-dd" */
async function serverDate(request: APIRequestContext): Promise<string> {
  const res = await request.get('/dev/clock');
  expect(res.ok()).toBeTruthy();
  return ((await res.json()).now as string).slice(0, 10);
}

async function moveClockTo(request: APIRequestContext, to: string) {
  const res = await request.post('/dev/clock', { data: { to } });
  expect(res.ok(), `POST /dev/clock ${to} -> ${res.status()} ${await res.text()}`).toBeTruthy();
}

/** 서버 날짜가 가을 회차(10/1~11/30) 밖이면 가장 가까운 앞날의 10월 2일로 민다(시계는 앞으로만 간다) */
async function ensureAutumn(request: APIRequestContext) {
  const [year, month] = (await serverDate(request)).split('-').map(Number);
  if (month === 10 || month === 11) return;
  await moveClockTo(request, `${month === 12 ? year + 1 : year}-10-02T12:00:00+09:00`);
}

async function seasonsOf(request: APIRequestContext, token: string): Promise<Seasons> {
  const res = await request.get('/seasons/current', { headers: { [EXPLORER_HEADER]: token } });
  expect(res.ok(), `GET /seasons/current -> ${res.status()}`).toBeTruthy();
  return res.json();
}

test.afterEach(async ({ request }) => {
  await request.delete('/dev/clock');
});

test.describe('계절 한정 테마와 재방문 도장', () => {
  test('가을 회차 기간에 단풍 명소 열 곳을 칠해 완성하고, 해가 바뀐 뒤 다시 다녀와 그해 도장과 2회차 색을 받는다', async ({ page, dev, request }) => {
    await ensureAutumn(request);
    await open(page);
    const me = await dev.explorerOf(page);
    const round = (await seasonsOf(request, me)).current[0];
    expect(round.roundId).toMatch(/^autumn-\d{4}$/);

    await test.step('가을 회차 기간에는 지도 위에 회차 배지가 뜨고, 도감의 계절 한정 칸에 남은 기간과 진행·완성 보상이 보인다', async () => {
      await expect(page.locator('#season-badge')).toContainText(`${round.name} 0/10`);
      await expect(page.locator('#season-badge')).toContainText('남음');
      await tab(page, 'sets');
      await expect(page.locator('#season-left')).toContainText('남음');
      await expect(page.locator('#season-have')).toHaveText('0 / 10');
      await expect(page.locator('#season-reward')).toHaveText(`보상: 칭호 「단풍 사냥꾼」 + 150 XP · ${round.year} 계절 배경`);
      await expect(page.locator('#season [data-season-region]')).toHaveCount(10);
    });

    await test.step('좁은 휴대폰 화면에서도 계절 배지가 지도 도구 버튼 네 개를 가리지 않아 모두 누를 수 있다', async () => {
      await tab(page, 'map');
      const wide = page.viewportSize();
      for (const width of [360, 400, 480]) {
        await page.setViewportSize({ width, height: 800 });
        await expect(page.locator('#season-badge')).toBeVisible();
        for (const tool of ['#t-paint', '#t-detail', '#t-reset-view', '#t-char']) {
          const button = page.locator(tool);
          await button.scrollIntoViewIfNeeded();
          // 버튼 한가운데를 누르면 그 버튼이 눌린다(다른 요소가 위에 있지 않다)
          const onTop = await button.evaluate(element => {
            const box = element.getBoundingClientRect();
            const hit = document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2);
            return !!hit && (hit === element || element.contains(hit));
          });
          expect(onTop, `${width}px 에서 ${tool} 이 가려졌다`).toBe(true);
          await button.click({ trial: true });
        }
      }
      await page.click('#t-detail');
      await expect(page.locator('#t-detail')).toHaveAttribute('aria-pressed', 'true');
      await page.click('#t-paint');
      if (wide) await page.setViewportSize(wide);
      await tab(page, 'sets');
    });

    await test.step('지도에서 보기를 누르면 지도로 돌아가 회차 지역 열 곳을 함께 강조한다', async () => {
      await page.click('#season-show');
      await expect(page.locator('#tab-map')).toBeVisible();
      await expect(page.locator('path.region.grp')).toHaveCount(10);
    });

    await test.step('기간 안에 아홉 곳을 칠하면 진행이 9/10 으로 오른다', async () => {
      const today = await serverDate(request);
      for (const code of AUTUMN.slice(0, 8)) await dev.checkIn(me, 'KR-' + code, today);
      // 화면 밖(API)에서 칠한 것은 진행에 늦게 반영되므로 서버가 셀 때까지 기다린 뒤 화면을 연다
      await expect.poll(async () => (await seasonsOf(request, me)).current[0].have, LATE).toBe(8);
      await open(page);
      await paint(page, AUTUMN[8]);
      await expect(page.locator('#season-badge')).toContainText(`${round.name} 9/10`, LATE);
    });

    await test.step('마지막 한 곳을 칠하면 회차 완성을 알리고, 칭호·XP·계절 배경을 받는다', async () => {
      await paint(page, AUTUMN[9]);
      await expect(toast(page, `${round.name} 완성!`)).toBeVisible(LATE);
      await expect(toast(page, `${round.name} 완성!`)).toContainText('칭호 「단풍 사냥꾼」 · +150 XP');
      await expect(page.locator('#season-badge')).toContainText(`${round.name} 10/10 · 완성`);
      await tab(page, 'sets');
      await expect(page.locator('#season-reward')).toHaveText(`완성 · 칭호 「단풍 사냥꾼」 +150 XP · ${round.year} 계절 배경을 받았어요`);
      await tab(page, 'bag');
      const background = page.locator(`#bag-inv [data-equip="${round.backgroundItemId}"]`);
      await expect(background).toHaveAttribute('data-origin', `${round.year} 계절 한정 테마 완성 보상`, LATE);
      await expect(background.locator('.ach')).toHaveText('🍁');
      await tab(page, 'profile');
      await expect(page.locator('#titles [data-title="season-autumn"]')).toBeEnabled();
      await tab(page, 'map');
    });

    await test.step('올해 처음 칠한 곳은 같은 해에는 도장을 받을 수 없다고 알려 준다', async () => {
      await pick(page, SOKCHO);
      await expect(page.locator('#d-revisit')).toBeDisabled();
      await expect(page.locator('#d-revisit-why')).toHaveText(`${round.year}년에 처음 칠한 곳이에요 — ${round.year + 1}년부터 도장을 받을 수 있어요`);
      await expect(page.locator('#d-stamps')).toContainText('아직 재방문 도장이 없어요');
    });

    await test.step('해가 바뀌면 "다시 다녀왔어요"로 그해 도장과 XP 를 받고, 같은 해에는 다시 받지 못한다', async () => {
      await moveClockTo(request, `${round.year + 1}-03-02T12:00:00+09:00`);
      await open(page);
      await pick(page, SOKCHO);
      await expect(page.locator('#d-revisit')).toBeEnabled();
      await page.click('#d-revisit');
      await expect(toast(page, `재방문 도장 · ${round.year + 1}`)).toBeVisible();
      await expect(toast(page, `재방문 도장 · ${round.year + 1}`)).toContainText('+10 XP · 도장 1개째');
      await expect(page.locator(`#d-stamps .stamp[data-year="${round.year + 1}"]`)).toBeVisible(LATE);
      await expect(page.locator('#d-revisit')).toBeDisabled();
      await expect(page.locator('#d-revisit-why')).toHaveText(`${round.year + 1}년 도장은 이미 받았어요 — ${round.year + 2}년에 다시 받을 수 있어요`);
    });

    await test.step('가방의 속초 특산물은 2회차 색으로 바뀌어 표시된다', async () => {
      await tab(page, 'bag');
      const specialty = page.locator(`#bag-inv [data-equip="region:KR-${SOKCHO}"]`);
      await expect(specialty).toHaveAttribute('data-variant', '2', LATE);
      await expect(specialty.locator('.variant')).toHaveText('2회차');
    });

    await test.step('계절 기간이 끝나면 배지는 사라지고, 도감에는 지난 가을 완성 기록과 다음 봄 회차가 보인다', async () => {
      await tab(page, 'map');
      await expect(page.locator('#season-badge')).toHaveCount(0);
      await tab(page, 'sets');
      await expect(page.locator('#season-left')).toHaveText('기간 아님');
      await expect(page.locator(`#season-history li[data-round="${round.roundId}"]`)).toHaveAttribute('data-completed', 'true');
      await expect(page.locator(`#season-history li[data-round="${round.roundId}"]`)).toHaveText(`🍁 ${round.name} · 10/10 · 완성`);
      await expect(page.locator('#season-next')).toContainText(`${round.year + 1} 벚꽃 명소 · 3월 20일부터`);
    });
  });
});

test.describe('가고 싶은 곳', () => {
  test('가고 싶은 곳에 꽂아 둔 곳을 칠하면 다녀옴으로 바뀌고, 이미 칠한 곳과 가득 찬 목록은 꽂을 수 없다고 알려 준다', async ({ page, dev, request }) => {
    await open(page);
    const me = await dev.explorerOf(page);

    await test.step('아직 칠하지 않은 곳에서 "가고 싶어요"를 누르면 지도에 핀이 꽂히고 가고 싶은 곳 목록에 들어간다', async () => {
      await pick(page, JONGNO);
      await page.click('#d-wish');
      await expect(toast(page, '가고 싶은 곳에 꽂았어요')).toContainText('종로구 · 칠하면 +20 XP');
      await expect(page.locator('#d-wish')).toHaveAttribute('aria-pressed', 'true');
      await expect(page.locator(`g.wish-pin[data-wish="${JONGNO}"]`)).toBeAttached();
      await expect(page.locator(`#wish-pending li[data-wish="${JONGNO}"]`)).toBeVisible();
      await expect(page.locator('#wish-count')).toHaveText('1 / 30');
    });

    await test.step('꽂아 둔 곳을 칠하면 다녀옴을 축하하고, 핀은 걷혀 다녀온 곳으로 옮겨 간다', async () => {
      await paint(page, JONGNO);
      await expect(toast(page, '가고 싶던 종로구에 다녀왔어요')).toBeVisible(LATE);
      await expect(toast(page, '가고 싶던 종로구에 다녀왔어요')).toContainText('+20 XP');
      await expect(page.locator('g.wish-pin')).toHaveCount(0);
      await expect(page.locator(`#wish-visited li[data-wish="${JONGNO}"]`)).toBeVisible();
      await expect(page.locator('#wish-count')).toHaveText('0 / 30');
      await pick(page, JONGNO);
      await expect(page.locator('#d-wish')).toHaveAttribute('data-state', 'visited');
    });

    await test.step('이미 칠한 곳은 "가고 싶어요"를 누를 수 없고, 대신 다시 다녀왔어요를 안내한다', async () => {
      await paint(page, JUNG);
      await pick(page, JUNG);
      await expect(page.locator('#d-wish')).toBeDisabled();
      await expect(page.locator('#d-wish')).toHaveAttribute('data-state', 'painted');
      await expect(page.locator('#d-wish-why')).toHaveText('이미 칠한 곳이에요 — 다시 가면 "다시 다녀왔어요"로 도장을 받을 수 있어요');
    });

    await test.step('그사이 다른 곳에서 서른 곳을 꽂아 목록이 가득 차면, 더 꽂으려 할 때 거절을 안내하고 버튼을 막는다', async () => {
      await pick(page, YONGSAN);
      await expect(page.locator('#d-wish')).toBeEnabled();
      const geo = await (await request.get('/catalog/regions.geojson')).json();
      const taken = new Set([JONGNO, JUNG, YONGSAN, '21310'].map(code => 'KR-' + code));
      const others: string[] = geo.features.map((feature: { properties: { code: string } }) => feature.properties.code).filter((code: string) => !taken.has(code)).slice(0, 30);
      for (const code of others) {
        const res = await request.put(`/wishlist/${code}`, { headers: { [EXPLORER_HEADER]: me } });
        expect(res.ok(), `PUT /wishlist/${code} -> ${res.status()}`).toBeTruthy();
      }
      await page.click('#d-wish');
      await expect(toast(page, '가고 싶은 곳이 가득해요')).toContainText('가고 싶은 곳은 30곳까지 꽂을 수 있어요');
      await expect(page.locator('#d-wish')).toHaveAttribute('data-state', 'full');
      await expect(page.locator('#d-wish')).toBeDisabled();
      await expect(page.locator('#d-wish-why')).toHaveText('가고 싶은 곳은 30곳까지 꽂을 수 있어요 — 다녀오거나 빼면 다시 꽂을 수 있어요');
      await expect(page.locator('#wish-count')).toHaveText('30 / 30');
      await expect(page.locator('g.wish-pin')).toHaveCount(30);
    });
  });
});
