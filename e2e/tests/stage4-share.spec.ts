import { test, expect } from '../fixtures';
import type { Browser, Page } from '@playwright/test';

/**
 * 4단계 파트 B(공유) — 칠하기 → /dev/login(파트 A 헬퍼) → 프로필 탭 카드 미리보기(서버 PNG) → 기본 비공개(404) → "공개하기"
 * → 로그아웃 상태로 공개 프로필 /u/{handle}:
 * 메모·사진 미노출, 월 단위 날짜, og:image 200 PNG → 공개 범위 PRIVATE 면 404 → 다시 공개 + 프로필에 지도 공개 → 다른 사람이 공개
 * 프로필의 "이 지도에 합류" 로 합류 → 양쪽 초대 보상(한정 EVENT 아이템).
 * 카드 원천 무효화·초대 보상은 outbox 릴레이(비동기)라 기대값은 기다린다.
 */

const JONGNO = '11010';
const MEMO = '아무도 보면 안 되는 메모';
const VISIT_DATE = '2026-09-17';
const EMAIL = 'share.kim+e2e@example.com';
const LATE = { timeout: 15_000 };

const region = (page: Page, code: string) => page.locator(`path.region[data-code="${code}"]`);
const tab = (page: Page, name: string) => page.locator(`#tabs [data-tab="${name}"]`).click();

/** 화면을 새로 연다(해시만 바뀌는 같은 문서 이동이 되지 않게 매번 새 주소로 — 로그인 세션을 다시 읽는다). */
let opened = 0;
async function open(page: Page) {
  await page.goto(`/?open=${++opened}`);
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
}

async function paintWithMemo(page: Page, code: string) {
  await page.click('#t-paint');
  await region(page, code).dispatchEvent('click');
  await expect(page.locator('#checkin')).toBeVisible();
  await page.fill('#ci-date', VISIT_DATE);
  await page.fill('#ci-memo', MEMO);
  await page.click('#ci-save');
  await expect(page.locator('#checkin')).toBeHidden();
  await expect(region(page, code)).toHaveClass(/\bon\b/);
}

/** 로그인하지 않은 새 브라우저(쿠키·localStorage 없음). */
async function stranger(browser: Browser) {
  const context = await browser.newContext();
  return { context, page: await context.newPage() };
}

test.describe('4단계 공유 ↔ 서버', () => {
  test('카드 미리보기 → 공개 프로필(월 단위·메모 없음·OG PNG) → 비공개 404 → 프로필 링크 합류 초대 보상', async ({ page, browser, request, dev }) => {
    // ---- 칠하기(메모 포함) → 로그인(익명 탐험가를 계정에 연결) ----
    await open(page);
    await paintWithMemo(page, JONGNO);
    const login = await dev.login(page, { email: EMAIL, token: await dev.explorerOf(page) });
    expect(login.outcome).toBe('LINKED');
    const handle = login.handle;

    // ---- 프로필 탭: 서버 PNG 카드 미리보기 3종 · 프로필 링크 · 공개 범위 ----
    await open(page);
    await tab(page, 'profile');
    await expect(page.locator('#share-url')).toHaveText(new RegExp(`/u/${handle}$`), LATE);
    await expect(page.locator('#t-copy')).toBeEnabled();
    // 기본 공개 범위 PRIVATE(사용자 결정 Q1) — "공개하기" 전에는 /u/{handle} 이 404
    await expect(page.locator('#privacy-select')).toHaveValue('PRIVATE');
    expect((await request.get(`/u/${handle}`)).status()).toBe(404);       // 방문자(세션 없음)
    expect((await page.request.get(`/u/${handle}`)).status()).toBe(200);  // 주인 본인은 비공개여도 미리보기(5단계 리더 결정 2)
    for (const kind of ['territory', 'recent', 'recap']) {
      const img = page.locator(`#card-${kind}`);
      await expect(img).toHaveAttribute('data-loaded', 'true', LATE);
      expect(await img.evaluate((el: HTMLImageElement) => el.naturalWidth)).toBe(1200);
    }
    await page.click('#t-share');
    await expect(page.locator('#modal')).toBeVisible();
    await expect.poll(() => page.locator('#share-img').evaluate((el: HTMLImageElement) => el.naturalHeight)).toBe(630);
    await page.click('#t-close');
    await page.click('#t-publish');
    await expect(page.locator('html')).toHaveAttribute('data-profile-visibility', 'PUBLIC');
    await expect(page.locator('#t-publish')).toBeHidden();

    // ---- 로그아웃 상태(새 브라우저)로 공개 프로필 ----
    const visitor = await stranger(browser);
    const profile = await visitor.page.goto(`/u/${handle}`);
    expect(profile?.status()).toBe(200);
    await expect(visitor.page.locator('#p-handle')).toHaveText(`@${handle}`);
    await expect(visitor.page.locator(`#p-recent li[data-region="KR-${JONGNO}"] time`)).toHaveText('2026년 9월');
    const html = await visitor.page.content();
    expect(html).not.toContain(MEMO);
    expect(html).not.toContain(VISIT_DATE);
    expect(html).not.toMatch(/\d{4}-\d{2}-\d{2}/);
    const ogImage = await visitor.page.locator('meta[property="og:image"]').getAttribute('content');
    expect(ogImage).toMatch(new RegExp(`/u/${handle}/card/territory\\.png$`));
    const png = await visitor.page.request.get(ogImage!);
    expect(png.status()).toBe(200);
    expect(png.headers()['content-type']).toBe('image/png');
    expect((await png.body()).subarray(1, 4).toString()).toBe('PNG');
    await expect(visitor.page.locator('meta[property="og:title"]')).toHaveAttribute('content', new RegExp(`@${handle}의 영토`));

    // ---- 공개 범위 PRIVATE → 공개 프로필·카드 404(존재 숨김) ----
    await page.selectOption('#privacy-select', 'PRIVATE');
    await expect(page.locator('html')).toHaveAttribute('data-profile-visibility', 'PRIVATE');
    expect((await visitor.page.request.get(`/u/${handle}`)).status()).toBe(404);
    expect((await visitor.page.request.get(`/u/${handle}/card/territory.png`)).status()).toBe(404);
    // FRIENDS(5단계) — 서로 팔로우한 친구에게만 열린다. 로그아웃 방문자에겐 PRIVATE 처럼 404
    await page.selectOption('#privacy-select', 'FRIENDS');
    await expect(page.locator('#privacy-note')).toContainText('서로 팔로우한 친구에게만');
    expect((await visitor.page.request.get(`/u/${handle}`)).status()).toBe(404);
    await page.click('#t-publish');
    await expect(page.locator('html')).toHaveAttribute('data-profile-visibility', 'PUBLIC');

    // ---- 공유 지도를 만들고 프로필에 공개(프로필 탭의 체크) ----
    // 세션 요청의 변경 메서드는 CSRF 헤더(쿠키 XSRF-TOKEN 값)가 필요하다(파트 A 규칙)
    const xsrf = (await page.context().cookies()).find(cookie => cookie.name === 'XSRF-TOKEN')?.value ?? '';
    const created = await page.request.post('/maps', { headers: { 'X-XSRF-TOKEN': xsrf }, data: { name: '부산 원정대' } });
    expect(created.status(), await created.text()).toBe(201);
    const map = await created.json();
    await open(page);
    await tab(page, 'profile');
    const toggle = page.locator(`[data-profile-map="${map.mapId}"]`);
    await expect(toggle).toBeVisible(LATE);
    await expect(toggle).not.toBeChecked();
    await toggle.check();
    await expect.poll(async () => (await (await visitor.page.request.get(`/u/${handle}`)).text()).includes(`data-join="${map.mapId}"`), LATE)
      .toBe(true);

    // ---- 다른 사람(익명)이 공개 프로필의 "이 지도에 합류" ----
    const guest = await stranger(browser);
    await open(guest.page); // 익명 탐험가 발급
    const guestToken = await dev.explorerOf(guest.page);
    await guest.page.goto(`/u/${handle}`);
    await guest.page.click(`[data-join="${map.mapId}"]`);
    await expect(guest.page.locator('html')).toHaveAttribute('data-joined-via', 'profile', LATE);
    await expect(guest.page.locator('html')).toHaveAttribute('data-map-id', map.mapId, LATE);

    // ---- 양쪽 초대 보상(한정 EVENT 아이템) ----
    await expect.poll(async () => {
      const inventory = await (await request.get('/inventory', { headers: { 'X-Explorer-Token': guestToken } })).json();
      return inventory.items.map((item: any) => item.itemId);
    }, LATE).toContain('invite:guest-ticket');
    await expect.poll(async () => {
      const inventory = await (await page.request.get('/inventory')).json();
      return inventory.items.map((item: any) => item.itemId);
    }, LATE).toContain('invite:host-flag');
    const guestInventory = await (await request.get('/inventory', { headers: { 'X-Explorer-Token': guestToken } })).json();
    expect(guestInventory.items.find((item: any) => item.itemId === 'invite:guest-ticket').source).toBe('EVENT');

    await visitor.context.close();
    await guest.context.close();
  });
});
