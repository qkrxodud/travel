import { test, expect } from '../fixtures';
import type { Page } from '@playwright/test';

/**
 * 4단계 파트 A(계정·로그인·claimExplorer 병합) — 구글 클라이언트 ID 없이 local /dev/login(실제 OIDC 성공과 같은 계정 연결·병합 경로)으로.
 * 익명 칠하기 → 로그인(연결: 영토 유지) → 두 번째 기기(새 브라우저 컨텍스트)의 다른 익명 탐험가로 칠한 뒤 같은 계정 로그인
 * → 병합 안내 · 합쳐진 영토 · XP(재계산과 일치) → 로그아웃하면 새 익명 탐험가.
 * 병합의 방문 이동·재계산은 outbox 릴레이·재계산 예약(비동기)이라 기대값은 넉넉히 기다린다.
 */

const JONGNO = '11010';     // 서울 종로구 — 첫 기기
const BUSAN_JUNG = '26010'; // 부산 중구 — 두 번째 기기(새 지역)
const JUNG = '11020';       // 서울 중구 — 두 기기 모두(두 번째 기기 쪽 방문일이 더 이름 → 그 기록이 남는다)
const EMAIL = 'kim.traveler+e2e@example.com';
const LATE = { timeout: 15_000 };

const region = (page: Page, code: string) => page.locator(`path.region[data-code="${code}"]`);
const tab = (page: Page, name: string) => page.locator(`#tabs [data-tab="${name}"]`).click();

async function open(page: Page) {
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
}

async function paint(page: Page, code: string) {
  await page.click('#t-paint');
  await region(page, code).dispatchEvent('click');
  await expect(page.locator('#checkin')).toBeVisible();
  await page.click('#ci-save');
  await expect(page.locator('#checkin')).toBeHidden();
  await expect(region(page, code)).toHaveClass(/\bon\b/);
}

test.describe('구글 로그인과 계정', () => {
  test('두 기기의 익명 기록을 한 계정으로 모으고 로그아웃한다', async ({ page, browser, dev }) => {
    const { firstId, firstToken } = await test.step('로그인하지 않아도 칠할 수 있고, 구글 설정이 없으면 로그인은 준비 중으로 보인다', async () => {
      await open(page);
      await tab(page, 'profile');
      await expect(page.locator('#t-google')).toBeDisabled();
      await expect(page.locator('#acct-note')).toContainText('준비 중');
      await tab(page, 'map');
      await paint(page, JONGNO);
      await paint(page, JUNG);
      const firstId = await dev.idOf(page);
      const firstToken = await dev.explorerOf(page);
      return { firstId, firstToken };
    });

    const { linked, before } = await test.step('처음 로그인하면 지금 익명 탐험가를 그대로 계정에 붙여 영토가 남는다', async () => {
      const linked = await dev.login(page, { email: EMAIL, token: firstToken });
      expect(linked.outcome).toBe('LINKED');
      expect(linked.explorerId).toBe(firstId);
      expect(linked.handle).toMatch(/^explorer-[a-z2-9]{4,6}$/); // 이메일과 무관한 랜덤 handle
      expect(linked.handle).not.toContain('kim');
      expect(linked.merge).toBeNull();
      await open(page);
      await expect(page.locator('html')).toHaveAttribute('data-logged-in', 'true');
      await expect(region(page, JONGNO)).toHaveClass(/\bon\b/); // 영토 유지
      await expect(region(page, JUNG)).toHaveClass(/\bon\b/);
      await tab(page, 'profile');
      await expect(page.locator('#acct-handle')).toHaveText('@' + linked.handle);
      await expect(page.locator('#acct-notice')).toContainText('계정에 연결');
      // 로그인하면 익명 토큰은 무효(세션으로만) — 화면은 토큰을 버렸다
      expect(await page.evaluate(() => localStorage.getItem('territory-explorer-token'))).toBeNull();
      const before = await (await page.request.get('/progress')).json();
      return { linked, before };
    });

    const { secondContext, second, secondToken } = await test.step('다른 기기에서는 다른 익명 탐험가로 새 지역과 같은 지역을 칠해 둔다', async () => {
      // 같은 지역(서울 중구)은 더 이른 날짜로
      const secondContext = await browser.newContext();
      const second = await secondContext.newPage();
      await open(second);
      await paint(second, BUSAN_JUNG);
      const secondToken = await dev.explorerOf(second);
      await dev.checkIn(secondToken, 'KR-' + JUNG, '2025-05-05', '두 번째 기기 메모');
      expect(await dev.idOf(second)).not.toBe(firstId);
      return { secondContext, second, secondToken };
    });

    await test.step('그 기기로 같은 계정에 로그인하면 익명 기록을 옮겼다고 알리고 두 영토가 합쳐진다', async () => {
      const merged = await dev.login(second, { email: EMAIL, token: secondToken });
      expect(merged.outcome).toBe('MERGED');
      expect(merged.explorerId).toBe(firstId);
      expect(merged.merge).toEqual({ fromExplorerId: expect.any(String), movedRegions: 2, newRegions: 1 });

      await open(second);
      await expect(second.locator('html')).toHaveAttribute('data-logged-in', 'true');
      await expect(second.locator('.toast', { hasText: '익명 기록 2곳을 계정으로 옮겼어요' })).toBeVisible();
      expect(await dev.idOf(second)).toBe(firstId); // 이제 계정 탐험가
      // 합쳐진 영토(방문 이동은 늦게 반영 — 다시 읽으며 기다린다). 같은 지역은 더 이른 방문이 남는다
      const today = await todayOf(page);
      await expect.poll(async () => {
        const territory = await (await second.request.get('/territory')).json();
        return territory.visits.map((visit: any) => visit.regionCode + '@' + visit.visitDate).sort();
      }, LATE).toEqual(['KR-11010@' + today, 'KR-11020@2025-05-05', 'KR-26010@' + today].sort());
      await second.reload();
      await expect(second.locator('html')).toHaveAttribute('data-territory', 'ready');
      for (const code of [JONGNO, JUNG, BUSAN_JUNG]) await expect(region(second, code)).toHaveClass(/\bon\b/, LATE);
      await tab(second, 'profile');
      await expect(second.locator('#acct-handle')).toHaveText('@' + linked.handle);
    });

    await test.step('합친 뒤 XP 는 새 지역만큼 늘고, 처음부터 다시 세어도 같은 값이다', async () => {
      // 병합 보상은 이벤트 반영 뒤 서버의 재계산 예약(약 2초 주기)으로 한 번 더 맞춰진다 — 중간값을 "다 반영된 값"으로 읽지 않도록
      // XP 가 3초 넘게 그대로일 때까지 기다린다(9단계 QA r2 P3-r2-4: 중간값 55 를 읽고 재계산 90 과 비교해 일시 실패)
      const readXp = async (): Promise<number> => (await (await second.request.get('/progress')).json()).xp;
      const seen: number[] = [];
      await expect.poll(async () => {
        seen.push(await readXp());
        const recent = seen.slice(-4);
        return recent.length === 4 && recent.every(xp => xp === recent[0]) && recent[0] > before.xp;
      }, { timeout: 30_000, intervals: [1000] }).toBe(true);
      const settledXp = await readXp();
      const recalculated = await (await second.request.post('/dev/recalculate')).json();
      expect(recalculated.deferred).toEqual([]);
      expect((await (await second.request.get('/progress')).json()).xp).toBe(settledXp);
    });

    await test.step('계정에 합쳐진 익명 탐험가로는 더 이상 들어올 수 없다', async () => {
      const stale = await second.request.get('/progress', { headers: { 'X-Explorer-Token': secondToken }, failOnStatusCode: false });
      expect(stale.status()).toBe(200); // 로그인 세션이 우선 — 옛 익명 표시는 무시된다
      const anonymousOnly = await browser.newContext();
      const staleAlone = await anonymousOnly.request.get('/progress', { headers: { 'X-Explorer-Token': secondToken } });
      expect(staleAlone.status()).toBe(401);
      await anonymousOnly.close();
    });

    await test.step('첫 기기도 같은 계정이라 다시 열면 다른 기기에서 칠한 곳이 보인다', async () => {
      await page.reload();
      await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
      await expect(region(page, BUSAN_JUNG)).toHaveClass(/\bon\b/, LATE);
    });

    await test.step('로그아웃하면 이 기기는 빈 지도의 새 익명 탐험가가 된다', async () => {
      await tab(second, 'profile');
      await second.click('#t-logout');
      await expect(second.locator('html')).toHaveAttribute('data-logged-in', 'false');
      await expect(second.locator('#t-google')).toBeVisible();
      await expect.poll(async () => second.evaluate(() => localStorage.getItem('territory-explorer-id')), LATE).not.toBe(firstId);
      await expect(region(second, BUSAN_JUNG)).not.toHaveClass(/\bon\b/);
    });

    await secondContext.close();
  });

  test('handle 을 바꾼다', async ({ page, browser, dev }) => {
    await open(page);
    const mine = await dev.login(page, { email: 'lee@example.com', token: await dev.explorerOf(page) });
    const other = await browser.newContext();
    const theirs = await dev.login(other.request, { email: 'park@example.com' });
    await open(page);
    await tab(page, 'profile');
    await expect(page.locator('#acct-handle')).toHaveText('@' + mine.handle);

    await test.step('형식이 틀리면 거절된다', async () => {
      await page.fill('#acct-handle-input', 'A!');
      await page.click('#t-handle');
      await expect(page.locator('.toast', { hasText: 'handle 형식 확인' })).toBeVisible();
    });

    await test.step('쓸 수 없는 말이면 거절된다', async () => {
      await page.fill('#acct-handle-input', 'admin');
      await page.click('#t-handle');
      await expect(page.locator('.toast', { hasText: '쓸 수 없는 handle' })).toBeVisible();
    });

    await test.step('남이 쓰는 handle 이면 거절된다', async () => {
      await page.fill('#acct-handle-input', theirs.handle);
      await page.click('#t-handle');
      await expect(page.locator('.toast', { hasText: '이미 쓰는 handle' })).toBeVisible();
    });

    await test.step('대문자를 섞어 적어도 소문자 handle 로 바뀐다', async () => {
      await page.fill('#acct-handle-input', 'Lee_Travels');
      await page.click('#t-handle');
      await expect(page.locator('#acct-handle')).toHaveText('@lee_travels');
    });

    await test.step('내가 놓은 옛 handle 은 남이 바로 가져가지 못한다', async () => {
      // 다른 사람 세션으로 직접 요청(쿠키 XSRF-TOKEN → 헤더)
      await other.request.get('/auth/session');
      const xsrf = (await other.cookies()).find(cookie => cookie.name === 'XSRF-TOKEN')!.value;
      const steal = await other.request.put('/me/handle', { headers: { 'X-XSRF-TOKEN': xsrf }, data: { handle: mine.handle } });
      expect(steal.status()).toBe(409);
      expect((await steal.json()).code).toBe('HANDLE_TAKEN');
    });

    await other.close();
  });
});

/** 화면의 "오늘"(서버와 같은 Asia/Seoul — 체크인 모달 기본 날짜). */
async function todayOf(page: Page): Promise<string> {
  return page.evaluate(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  });
}
