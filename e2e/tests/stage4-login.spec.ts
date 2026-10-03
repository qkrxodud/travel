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

test.describe('4단계 로그인 ↔ 서버', () => {
  test('익명 칠하기 → 로그인(영토 유지) → 다른 기기 익명 기록을 같은 계정으로 병합 → 로그아웃', async ({ page, browser, dev }) => {
    // ---- 첫 기기: 익명으로 칠하기, 구글 로그인은 비활성 안내(클라이언트 ID 없음) ----
    await open(page);
    await tab(page, 'profile');
    await expect(page.locator('#t-google')).toBeDisabled();
    await expect(page.locator('#acct-note')).toContainText('준비 중');
    await tab(page, 'map');
    await paint(page, JONGNO);
    await paint(page, JUNG);
    const firstId = await dev.idOf(page);
    const firstToken = await dev.explorerOf(page);

    // ---- 로그인(첫 계정 연결) — 지금 익명 탐험가를 그대로 계정에 연결(병합 없음) ----
    const linked = await dev.login(page, { email: EMAIL, token: firstToken });
    expect(linked.outcome).toBe('LINKED');
    expect(linked.explorerId).toBe(firstId);
    expect(linked.handle).toMatch(/^explorer-[a-z2-9]{4,6}$/); // 이메일과 무관한 랜덤(사용자 결정 Q1)
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

    // ---- 두 번째 기기: 다른 익명 탐험가로 칠하기(새 지역 + 같은 지역을 더 이른 날짜로) ----
    const secondContext = await browser.newContext();
    const second = await secondContext.newPage();
    await open(second);
    await paint(second, BUSAN_JUNG);
    const secondToken = await dev.explorerOf(second);
    await dev.checkIn(secondToken, 'KR-' + JUNG, '2025-05-05', '두 번째 기기 메모');
    expect(await dev.idOf(second)).not.toBe(firstId);

    // ---- 같은 계정으로 로그인 → 기존 계정으로 병합(사용자 확정) ----
    const merged = await dev.login(second, { email: EMAIL, token: secondToken });
    expect(merged.outcome).toBe('MERGED');
    expect(merged.explorerId).toBe(firstId);
    expect(merged.merge).toEqual({ fromExplorerId: expect.any(String), movedRegions: 2, newRegions: 1 });

    await open(second);
    await expect(second.locator('html')).toHaveAttribute('data-logged-in', 'true');
    await expect(second.locator('.toast', { hasText: '익명 기록 2곳을 계정으로 옮겼어요' })).toBeVisible();
    expect(await dev.idOf(second)).toBe(firstId); // 이제 계정 탐험가
    // 합쳐진 영토(방문 이동은 비동기 — 다시 읽으며 기다린다)
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

    // 진행(XP)은 재계산 예약으로 맞춰진다 — 새 지역(부산 중구)만큼 늘고, 다시 재계산해도 같다(이벤트 누적 = 재계산)
    await expect.poll(async () => (await (await second.request.get('/progress')).json()).xp, LATE).toBeGreaterThan(before.xp);
    const settledXp = (await (await second.request.get('/progress')).json()).xp;
    const recalculated = await (await second.request.post('/dev/recalculate')).json();
    expect(recalculated.deferred).toEqual([]);
    expect((await (await second.request.get('/progress')).json()).xp).toBe(settledXp);

    // 병합된 익명 탐험가의 토큰은 무효
    const stale = await second.request.get('/progress', { headers: { 'X-Explorer-Token': secondToken }, failOnStatusCode: false });
    expect(stale.status()).toBe(200); // 세션이 우선 — 토큰 헤더는 무시된다
    const anonymousOnly = await browser.newContext();
    const staleAlone = await anonymousOnly.request.get('/progress', { headers: { 'X-Explorer-Token': secondToken } });
    expect(staleAlone.status()).toBe(401);
    await anonymousOnly.close();

    // 첫 기기도 같은 계정 — 새로 읽으면 부산 중구가 보인다
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    await expect(region(page, BUSAN_JUNG)).toHaveClass(/\bon\b/, LATE);

    // ---- 로그아웃 → 이 기기는 새 익명 탐험가로 ----
    await tab(second, 'profile');
    await second.click('#t-logout');
    await expect(second.locator('html')).toHaveAttribute('data-logged-in', 'false');
    await expect(second.locator('#t-google')).toBeVisible();
    await expect.poll(async () => second.evaluate(() => localStorage.getItem('territory-explorer-id')), LATE).not.toBe(firstId);
    await expect(region(second, BUSAN_JUNG)).not.toHaveClass(/\bon\b/);
    await secondContext.close();
  });

  test('handle 변경 — 형식·금칙어·중복·옛 handle 예약', async ({ page, browser, dev }) => {
    await open(page);
    const mine = await dev.login(page, { email: 'lee@example.com', token: await dev.explorerOf(page) });
    const other = await browser.newContext();
    const theirs = await dev.login(other.request, { email: 'park@example.com' });
    await open(page);
    await tab(page, 'profile');
    await expect(page.locator('#acct-handle')).toHaveText('@' + mine.handle);

    await page.fill('#acct-handle-input', 'A!');
    await page.click('#t-handle');
    await expect(page.locator('.toast', { hasText: 'handle 형식 확인' })).toBeVisible();
    await page.fill('#acct-handle-input', 'admin');
    await page.click('#t-handle');
    await expect(page.locator('.toast', { hasText: '쓸 수 없는 handle' })).toBeVisible();
    await page.fill('#acct-handle-input', theirs.handle);
    await page.click('#t-handle');
    await expect(page.locator('.toast', { hasText: '이미 쓰는 handle' })).toBeVisible();
    await page.fill('#acct-handle-input', 'Lee_Travels');
    await page.click('#t-handle');
    await expect(page.locator('#acct-handle')).toHaveText('@lee_travels');
    // 놓은 랜덤 handle 은 예약 — 다른 사람이 바로 가져갈 수 없다(쿠키 XSRF-TOKEN → 헤더)
    await other.request.get('/auth/session');
    const xsrf = (await other.cookies()).find(cookie => cookie.name === 'XSRF-TOKEN')!.value;
    const steal = await other.request.put('/me/handle', { headers: { 'X-XSRF-TOKEN': xsrf }, data: { handle: mine.handle } });
    expect(steal.status()).toBe(409);
    expect((await steal.json()).code).toBe('HANDLE_TAKEN');
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
