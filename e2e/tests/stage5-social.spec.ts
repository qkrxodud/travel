import { test, expect } from '../fixtures';
import type { Browser, Page } from '@playwright/test';

/**
 * 5단계(소셜) — 두 사용자(브라우저 컨텍스트 2개, /dev/login): 랭킹 탭에서 handle 로 팔로우 → 상대가 맞팔로우(친구) → 친구 랭킹·친구 소식
 * 반영 → 이름을 눌러 영토 비교(나만·둘 다·상대만 칸·지도 색) → 친구 공개(FRIENDS) 프로필은 맞팔에게만 열림 → 언팔하면 404 → 공유 지도
 * 안 랭킹(이의 표시 지역 제외) → 상위 % 표시(dev 배치 실행 뒤) → 익명은 로그인 유도.
 * 친구 소식·탐험가 단위 지역 수는 outbox 릴레이(비동기)로 반영되므로 기대값은 기다린다.
 */

const JONGNO = 'KR-11010';
const ULLEUNG = 'KR-37430';    // 경북 울릉군(전설)
const GYEONGJU = 'KR-37020';   // 경북 경주시
const ULSAN_JUNG = 'KR-26010'; // 울산 중구
const LATE = { timeout: 15_000 };

const tab = (page: Page, name: string) => page.locator(`#tabs [data-tab="${name}"]`).click();

let opened = 0;
async function open(page: Page) {
  await page.goto(`/?open=${++opened}`);
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
}

/** 세션 요청의 변경 메서드는 CSRF 헤더(쿠키 XSRF-TOKEN 값)가 필요하다(4단계 규칙). 화면을 한 번 연 뒤에 부른다. */
async function xsrf(page: Page) {
  return (await page.context().cookies()).find(cookie => cookie.name === 'XSRF-TOKEN')?.value ?? '';
}

async function send(page: Page, method: 'post' | 'put' | 'delete', url: string, data?: unknown) {
  const res = await page.request[method](url, { headers: { 'X-XSRF-TOKEN': await xsrf(page) }, data });
  expect(res.ok(), `${method.toUpperCase()} ${url} -> ${res.status()} ${await res.text()}`).toBeTruthy();
  return res.status() === 204 ? null : res.json();
}

async function checkIn(page: Page, regionCode: string, mapId?: string) {
  await send(page, 'post', '/visits', { regionCode, visitDate: '2026-10-01', ...(mapId ? { mapId } : {}) });
}

async function user(browser: Browser, dev: any, email: string) {
  const context = await browser.newContext();
  const page = await context.newPage();
  const login = await dev.login(page, { email });
  await open(page);
  await expect(page.locator('html')).toHaveAttribute('data-logged-in', 'true');
  return { context, page, handle: login.handle as string, explorerId: login.explorerId as string };
}

test.describe('친구와 랭킹', () => {
  test('서로 팔로우하면 친구 랭킹·소식·영토 비교가 열리고 친구 공개 프로필을 볼 수 있으며, 팔로우를 끊으면 다시 볼 수 없고, 공유 지도 안 랭킹과 전국 상위 % 가 보인다', async ({ browser, dev }) => {
    const kim = await user(browser, dev, 'kim.social+e2e@example.com');
    const lee = await user(browser, dev, 'lee.social+e2e@example.com');
    await checkIn(kim.page, JONGNO);
    await checkIn(kim.page, ULLEUNG);
    await checkIn(lee.page, JONGNO);
    await checkIn(lee.page, ULSAN_JUNG);
    await send(lee.page, 'put', '/me/privacy', { visibility: 'FRIENDS' });

    // 주인 본인은 친구 공개여도 자기 공개 프로필을 본다(미리보기)
    expect((await lee.page.request.get(`/u/${lee.handle}`)).status()).toBe(200);

    // ---- 김: 랭킹 탭에서 handle 로 팔로우 — 이는 친구 공개(김에게 숨은 프로필)라 없는 handle 과 같은 응답, 팔로우는 남는다 ----
    await open(kim.page);
    await tab(kim.page, 'rank');
    await expect(kim.page.locator('html')).toHaveAttribute('data-social', /\d+/);
    await expect(kim.page.locator('#friends-login')).toBeHidden();
    await expect(kim.page.locator('#rank li.me')).toContainText(`나 (@${kim.handle})`);
    await kim.page.fill('#follow-handle', '@' + lee.handle);
    await kim.page.click('#t-follow');
    await expect(kim.page.locator('.toast').filter({ hasText: '상대도 나를 팔로우해야 친구가 돼요' })).toBeVisible();
    await expect(kim.page.locator(`#friends li[data-handle="${lee.handle}"]`)).toHaveCount(0); // 존재를 드러내지 않는다
    expect((await kim.page.request.get(`/u/${lee.handle}`)).status()).toBe(404);
    await expect(kim.page.locator(`#rank li[data-handle="${lee.handle}"]`)).toHaveCount(0);

    // ---- 이: 나를 팔로우한 김을 맞팔로우 → 친구 ----
    await open(lee.page);
    await tab(lee.page, 'rank');
    const kimInLee = lee.page.locator(`#friends li[data-handle="${kim.handle}"]`);
    await expect(kimInLee).toHaveAttribute('data-relation', 'follower', LATE);
    await kimInLee.locator('[data-follow]').click();
    await expect(lee.page.locator('.toast').filter({ hasText: '친구가 됐어요' })).toBeVisible();
    await expect(kimInLee).toHaveAttribute('data-relation', 'mutual');
    // 김은 비공개(기본) — 맞팔이어도 이의 친구 랭킹엔 안 보인다(리더 결정 1)
    await expect(lee.page.locator(`#rank li[data-handle="${kim.handle}"]`)).toHaveCount(0);

    // ---- 김: 친구 랭킹·친구 소식(릴레이 뒤) ----
    await expect.poll(async () => (await (await kim.page.request.get('/feed')).json()).items
      .filter((item: any) => item.kind === 'VISIT').map((item: any) => item.regionCode), LATE).toContain(ULSAN_JUNG);
    await open(kim.page);
    await tab(kim.page, 'rank');
    const leeRow = kim.page.locator(`#rank li[data-handle="${lee.handle}"]`);
    await expect(leeRow).toBeVisible(LATE);
    await expect(leeRow.locator('.sc')).toContainText('2');
    await expect(kim.page.locator('#rank-baseline')).toBeHidden();
    await expect(kim.page.locator(`#feed li[data-kind="VISIT"][data-region="${ULSAN_JUNG}"]`)).toContainText('@' + lee.handle);
    await expect(kim.page.locator(`#feed li[data-kind="VISIT"][data-region="${ULSAN_JUNG}"] time`)).toHaveText('오늘');
    await expect(kim.page.locator('#feed')).not.toContainText('2026-10-01'); // 정확한 날짜 없음

    // ---- 영토 비교: 이름을 누르면 나만·둘 다·상대만 + 지도 색 ----
    await leeRow.locator('button').click();
    await expect(kim.page.locator('html')).toHaveAttribute('data-vs', lee.handle);
    await expect(kim.page.locator('#vs-sub')).toContainText('@' + lee.handle);
    await expect(kim.page.locator('#vs-sub')).toContainText('모든 지도 기준');
    await expect(kim.page.locator('[data-vs-only-mine]')).toHaveAttribute('data-vs-only-mine', '1');
    await expect(kim.page.locator('[data-vs-both]')).toHaveAttribute('data-vs-both', '1');
    await expect(kim.page.locator('[data-vs-only-theirs]')).toHaveAttribute('data-vs-only-theirs', '1');
    await expect(kim.page.locator(`#vs-map path[data-code="37430"]`)).toHaveClass(/vs-mine/);
    await expect(kim.page.locator(`#vs-map path[data-code="11010"]`)).toHaveClass(/vs-both/);
    await expect(kim.page.locator(`#vs-map path[data-code="26010"]`)).toHaveClass(/vs-theirs/);

    // ---- 친구 공개 프로필: 맞팔에게만 ----
    expect((await kim.page.request.get(`/u/${lee.handle}`)).status()).toBe(200);
    const stranger = await browser.newContext();
    expect((await stranger.request.get(`/u/${lee.handle}`)).status()).toBe(404);

    // ---- 언팔로우 → 다시 404 ----
    await kim.page.locator(`#friends li[data-handle="${lee.handle}"] [data-unfollow]`).click();
    await expect(kim.page.locator('.toast').filter({ hasText: '언팔로우' })).toBeVisible();
    await expect(kim.page.locator(`#friends li[data-handle="${lee.handle}"]`)).toHaveAttribute('data-relation', 'follower');
    expect((await kim.page.request.get(`/u/${lee.handle}`)).status()).toBe(404);
    await expect(kim.page.locator(`#rank li[data-handle="${lee.handle}"]`)).toHaveCount(0);
    await expect(kim.page.locator('#rank-baseline')).toBeVisible(); // 친구 0명 → 지역 평균 비교(배치 전 안내)

    // ---- 공유 지도 안 랭킹: 이의 표시 지역 제외 ----
    const map = await send(kim.page, 'post', '/maps', { name: '울릉 원정대' });
    await send(lee.page, 'post', '/maps/join', { inviteCode: map.inviteCode });
    await checkIn(kim.page, ULLEUNG, map.mapId);
    await checkIn(lee.page, ULLEUNG, map.mapId);
    await checkIn(lee.page, GYEONGJU, map.mapId);
    await send(kim.page, 'put', `/maps/${map.mapId}/visits/${GYEONGJU}/${lee.explorerId}/dispute`, { disputed: true });
    await open(kim.page);
    await kim.page.selectOption('#map-select', map.mapId);
    await expect(kim.page.locator('html')).toHaveAttribute('data-map-id', map.mapId);
    await tab(kim.page, 'rank');
    await expect(kim.page.locator('#map-rank-card')).toBeVisible(LATE);
    await expect(kim.page.locator('#map-rank-sub')).toContainText('이의 표시 1건 제외');
    const mapRows = kim.page.locator('#map-rank li');
    await expect(mapRows).toHaveCount(2);
    await expect(mapRows.nth(0)).toHaveAttribute('data-explorer', kim.explorerId);
    await expect(mapRows.nth(0).locator('.sc')).toContainText('1');
    await expect(mapRows.nth(1)).toHaveAttribute('data-explorer', lee.explorerId);
    await expect(mapRows.nth(1).locator('.sc')).toContainText('1'); // 경주시(이의)는 빠지고 울릉군만

    // ---- 상위 %: 배치 전 "—" → dev 배치 → 서버 값 ----
    await expect(kim.page.locator('#s-top')).toHaveText('—');
    await expect.poll(async () => (await (await lee.page.request.get('/rankings/friends')).json()).rows[0].regionCount, LATE).toBe(4); // 이: 종로·울산 중구 + 공유 지도 울릉·경주(이의 표시는 탐험가 단위와 무관)
    const batch = await (await kim.page.request.post('/dev/batch/rank')).json();
    expect(batch.population).toBe(2);
    await open(kim.page);
    await expect(kim.page.locator('html')).toHaveAttribute('data-percentile', '100');
    await expect(kim.page.locator('#s-top')).toHaveText('100');
    await expect(kim.page.locator('#s-rank')).toContainText('2위 / 2명');
    await open(lee.page);
    await expect(lee.page.locator('#s-top')).toHaveText('50');

    // ---- 익명: 랭킹 탭에서 로그인 유도, 본인 줄은 보인다 ----
    const guest = await browser.newContext();
    const guestPage = await guest.newPage();
    await open(guestPage);
    await tab(guestPage, 'rank');
    await expect(guestPage.locator('html')).toHaveAttribute('data-social', /\d+/);
    await expect(guestPage.locator('#friends-login')).toBeVisible();
    await expect(guestPage.locator('#follow-row')).toBeHidden();
    await expect(guestPage.locator('#rank li.me')).toContainText('나 (Kobi)');
    await expect(guestPage.locator('#rank-baseline')).toContainText('전국 평균 유저');

    await Promise.all([kim.context.close(), lee.context.close(), stranger.close(), guest.close()]);
  });
});
