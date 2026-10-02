import { test, expect } from '../fixtures';
import type { Page } from '@playwright/test';

/**
 * 3단계 파트 A(공유 지도) — 브라우저 컨텍스트 2개(지도장 A, 친구 B)로 지도 탭의 공유 지도 흐름을 확인한다.
 * 만들기(3줄 규칙) → 초대코드 합류 → 각자 체크인·지역 색 = 선점자 색 → 지도장 이의 → 탈퇴 경고(내 영토 N곳) → 재가입 복구.
 * 탈퇴 숨김·재가입 복구·선점 이전은 outbox 릴레이(비동기)로 반영되므로 기대값은 넉넉히 기다린다.
 */

const BUSAN_JUNG = '26010';  // 부산 중구 — A 가 먼저(선점), B 도 칠함
const BUSAN_SEO = '26020';   // 부산 서구 — B 혼자
const LATE = { timeout: 10_000 };

const region = (page: Page, code: string) => page.locator(`path.region[data-code="${code}"]`);
const toast = (page: Page, text: string | RegExp) => page.locator('.toast', { hasText: text });

async function open(page: Page) {
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
  await expect(page.locator('#map-select option')).toHaveCount(1, LATE); // 개인 지도
}

async function paint(page: Page, code: string) {
  await page.click('#t-paint');
  await region(page, code).dispatchEvent('click');
  await expect(page.locator('#checkin')).toBeVisible();
  await page.click('#ci-save');
  await expect(page.locator('#checkin')).toBeHidden();
  await expect(region(page, code)).toHaveClass(/\bon\b/);
}

/** 지도 데이터 다시 읽기(다른 사람의 체크인은 화면이 자동으로 모른다) — 지도 선택을 같은 값으로 다시 고른다. */
async function reloadMap(page: Page) {
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
}

test.describe('3단계 공유 지도 ↔ 서버', () => {
  test('만들기 → 초대코드 합류 → 각자 체크인·선점 색 → 지도장 이의 → 탈퇴 경고 → 재가입 복구', async ({ page, browser, dev }) => {
    // ---- A: 지도 만들기(3줄 규칙 안내) ----
    await open(page);
    const aId = await dev.idOf(page);
    await page.click('#t-map-new');
    await page.fill('#mm-name', '부산 원정대');
    await page.click('#mm-create');
    await expect(page.locator('#mm-rules li')).toHaveCount(3);
    await expect(page.locator('#mm-rules')).toContainText('선점자');
    const inviteCode = (await page.locator('#mm-invite').textContent())!.trim();
    expect(inviteCode).toMatch(/^[A-Z2-9]{8}$/);
    await page.click('#mm-done');
    await expect(page.locator('#map-kind')).toHaveText('공유 지도');
    await expect(page.locator('#map-invite')).toHaveText(inviteCode);
    await expect(page.locator('#map-members li[data-member]')).toHaveCount(1);
    await expect(page.locator('#map-settings')).toBeVisible(); // 지도장만 설정
    const sharedMapId = await page.locator('html').getAttribute('data-map-id');

    // ---- B: 다른 브라우저 컨텍스트에서 초대코드로 합류 ----
    const friendContext = await browser.newContext();
    const friend = await friendContext.newPage();
    await open(friend);
    const bId = await dev.idOf(friend);
    await friend.click('#t-map-join');
    await friend.fill('#mm-code', inviteCode.toLowerCase());
    await friend.click('#mm-join');
    await expect(toast(friend, '부산 원정대')).toBeVisible();
    await expect(friend.locator('#map-kind')).toHaveText('공유 지도');
    await expect(friend.locator('html')).toHaveAttribute('data-map-id', sharedMapId!);
    await expect(friend.locator('#map-members li[data-member]')).toHaveCount(2);
    await expect(friend.locator('#map-settings')).toBeHidden(); // 멤버는 설정 못 함
    await expect(friend.locator('#map-select option')).toHaveCount(2);

    // ---- 각자 체크인: A 먼저 중구(선점), B 중구·서구 ----
    await paint(page, BUSAN_JUNG);
    await paint(friend, BUSAN_JUNG);
    await paint(friend, BUSAN_SEO);

    // 지역 색 = 선점자 색
    await reloadMap(page);
    await expect(region(page, BUSAN_JUNG)).toHaveAttribute('data-claim', aId);
    await expect(region(page, BUSAN_SEO)).toHaveAttribute('data-claim', bId);
    const aColor = await page.locator(`#map-members li[data-member="${aId}"]`).getAttribute('data-color');
    const bColor = await page.locator(`#map-members li[data-member="${bId}"]`).getAttribute('data-color');
    expect(aColor).not.toEqual(bColor);
    const fillOf = (code: string) => region(page, code).evaluate(element => (element as SVGPathElement).style.fill);
    expect(await fillOf(BUSAN_SEO)).not.toEqual(await fillOf(BUSAN_JUNG));
    await expect(region(page, BUSAN_JUNG)).toHaveClass(/\bon\b/);     // A 의 영토
    await expect(region(page, BUSAN_SEO)).not.toHaveClass(/\bon\b/); // B 혼자 칠한 곳
    await expect(page.locator(`#map-members li[data-member="${bId}"]`)).toContainText('영토 2');

    // ---- 지도장 이의: A 가 B 의 서구 방문에 이의 표시 ----
    await page.click('#t-detail');
    await region(page, BUSAN_SEO).dispatchEvent('click');
    await page.locator(`#d-members li[data-visitor="${bId}"] [data-dispute]`).click();
    await expect(page.locator(`#d-members li[data-visitor="${bId}"] .tag-disputed`)).toBeVisible();
    await expect(page.locator('#log .tag-disputed')).toHaveCount(1);
    await page.click('#t-paint');

    // ---- B 탈퇴: 경고에 "내 영토 2곳" ----
    await reloadMap(friend);
    await friend.click('#t-map-leave');
    await expect(friend.locator('#leave-text')).toContainText('내 영토 2곳이 지도에서 사라집니다');
    await friend.click('#leave-ok');
    await expect(toast(friend, '지도에서 탈퇴')).toBeVisible();
    await expect(friend.locator('#map-kind')).toHaveText('개인 지도');
    await expect(friend.locator('#map-select option')).toHaveCount(1);

    // A 화면: B 의 방문이 숨겨져 서구는 지도에서 사라지고, 중구 선점은 A 그대로
    await expect(async () => {
      await reloadMap(page);
      await expect(region(page, BUSAN_SEO)).not.toHaveAttribute('data-claim', /.+/);
    }).toPass(LATE);
    await expect(region(page, BUSAN_JUNG)).toHaveAttribute('data-claim', aId);
    await expect(page.locator('#map-members li[data-member]')).toHaveCount(1);
    await expect(page.locator('#map-members')).toContainText('탈퇴 유예 중 1명');

    // ---- B 재가입(유예 안): 영토 복구 ----
    await friend.click('#t-map-join');
    await friend.fill('#mm-code', inviteCode);
    await friend.click('#mm-join');
    await expect(toast(friend, '돌아왔어요')).toBeVisible();
    await expect(async () => {
      await reloadMap(friend);
      await expect(region(friend, BUSAN_JUNG)).toHaveClass(/\bon\b/);
      await expect(region(friend, BUSAN_SEO)).toHaveClass(/\bon\b/);
    }).toPass(LATE);
    await expect(region(friend, BUSAN_SEO)).toHaveAttribute('data-claim', bId);
    await expect(region(friend, BUSAN_JUNG)).toHaveAttribute('data-claim', aId); // 선점은 돌아오지 않음(원래 A)
    await expect(friend.locator(`#map-members li[data-member="${bId}"]`)).toContainText('영토 2');

    await friendContext.close();
  });

  test('지도장 설정(사진 필수)과 초대코드 재발급', async ({ page }) => {
    await open(page);
    await page.click('#t-map-new');
    await page.fill('#mm-name', '사진 원정대');
    await page.click('#mm-create');
    await page.click('#mm-done');
    const before = (await page.locator('#map-invite').textContent())!.trim();
    await page.click('#t-invite-regen');
    await expect(page.locator('#map-invite')).not.toHaveText(before);

    await page.check('#set-photo');
    await page.fill('#set-cap', '3');
    await page.click('#t-settings-save');
    await expect(toast(page, '지도 설정 저장')).toBeVisible();

    // 사진 없이 체크인 → 422 PHOTO_REQUIRED 안내, 사진 주소를 넣으면 성공
    await region(page, BUSAN_JUNG).dispatchEvent('click');
    await expect(page.locator('#ci-photo')).toBeVisible();
    await page.click('#ci-save');
    await expect(toast(page, '사진이 필요해요')).toBeVisible();
    await page.fill('#ci-photo', 'https://example.com/busan.jpg');
    await page.click('#ci-save');
    await expect(page.locator('#checkin')).toBeHidden();
    await expect(region(page, BUSAN_JUNG)).toHaveClass(/\bon\b/);
  });
});
