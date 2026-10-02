import { test, expect, EXPLORER_HEADER } from '../fixtures';
import type { Page } from '@playwright/test';

/**
 * 1단계(카탈로그 + 탐험) — 지도 탭이 서버 API에 연결됐는지 확인한다.
 * 방문 기록의 진실 원천은 서버: 칠하기 → POST /visits, 기록 수정 → PATCH, 제거 → DELETE, 화면은 GET /territory 기준.
 */

const GAPYEONG = '31370'; // 경기 가평군(희귀) → 예상 XP 20 + 경기 첫 방문 15 + 선점 10 = 45
const JONGNO = '11010'; // 서울 종로구(일반)

const region = (page: Page, code: string) => page.locator(`path.region[data-code="${code}"]`);
const toast = (page: Page, text: string | RegExp) => page.locator('.toast', { hasText: text });

function localISO(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

async function open(page: Page) {
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
}

async function clickRegion(page: Page, code: string) {
  // 작은 지역도 겹침 없이 누르도록 d3 click 핸들러에 직접 이벤트를 보낸다
  await region(page, code).dispatchEvent('click');
}

test.describe('1단계 지도 탭 ↔ 서버', () => {
  test('칠하기 → 예상 XP → 저장 → 색칠·정복률·일지 → 새로고침 유지 → 기록 수정 → 제거', async ({ page, dev, request }) => {
    await open(page);
    await expect(page.locator('#s-cnt')).toHaveText('0 / 250');
    await expect(page.locator('#log')).toContainText('아직 기록이 없어요');

    // 지역 클릭 → 미리보기 API로 예상 XP·아이템을 보여주는 모달
    await clickRegion(page, GAPYEONG);
    const modal = page.locator('#checkin');
    await expect(modal).toBeVisible();
    await expect(page.locator('#ci-xp-total')).toHaveText('+45');
    await expect(page.locator('#ci-gains')).toContainText('희귀 지역 기본');
    await expect(page.locator('#ci-gains')).toContainText('경기 첫 발 도장');
    await expect(page.locator('#ci-gains')).toContainText('선점 보너스');
    await expect(page.locator('#ci-item')).toContainText('가평');
    await expect(modal).toContainText('1번째 영토');
    await expect(region(page, GAPYEONG)).not.toHaveClass(/\bon\b/); // 아직 저장 전

    await page.fill('#ci-memo', '잣 먹음');
    await page.click('#ci-save');
    await expect(modal).toBeHidden();

    // 지도 색칠 + 헤더 정복률 + 시·도별 + 일지
    await expect(region(page, GAPYEONG)).toHaveClass(/\bon\b/);
    await expect(page.locator('#s-cnt')).toHaveText('1 / 250');
    await expect(page.locator('#s-pct')).toHaveText('0');
    await expect(page.locator('#prov button[data-prov="경기"]')).toContainText('1/42');
    await expect(page.locator('#log')).toContainText('경기 가평군');
    await expect(page.locator('#log')).toContainText('잣 먹음');

    // 서버에 실제로 저장됐는지
    const explorerId = await dev.explorerOf(page);
    const t = await (await request.get('/territory', { headers: { [EXPLORER_HEADER]: explorerId } })).json();
    expect(t.conquest.visited).toBe(1);
    expect(t.visits[0].regionCode).toBe('KR-31370');
    expect(t.visits[0].memo).toBe('잣 먹음');

    // 새로고침해도 유지(서버가 진실 원천)
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    await expect(region(page, GAPYEONG)).toHaveClass(/\bon\b/);
    await expect(page.locator('#s-cnt')).toHaveText('1 / 250');
    await expect(page.locator('#log')).toContainText('잣 먹음');

    // 기록 보기 모드에서 수정 → PATCH
    await page.click('#t-detail');
    await clickRegion(page, GAPYEONG);
    await expect(page.locator('#d-memo')).toHaveValue('잣 먹음');
    await page.fill('#d-memo', '잣막걸리 한 잔');
    await page.fill('#d-date', '2025-05-05');
    await page.click('#d-save');
    await expect(toast(page, '기록 저장됨')).toBeVisible();
    await expect(page.locator('#log')).toContainText('잣막걸리 한 잔');
    await expect(page.locator('#log')).toContainText('2025-05-05');
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    await expect(page.locator('#log')).toContainText('잣막걸리 한 잔');

    // 영토에서 제거 → DELETE
    await page.click('#t-detail');
    await clickRegion(page, GAPYEONG);
    await page.click('#d-remove');
    await expect(region(page, GAPYEONG)).not.toHaveClass(/\bon\b/);
    await expect(page.locator('#s-cnt')).toHaveText('0 / 250');
    await expect(page.locator('#log')).toContainText('아직 기록이 없어요');
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    await expect(region(page, GAPYEONG)).not.toHaveClass(/\bon\b/);
  });

  test('칠하기 모드에서 이미 칠한 지역을 누르면 제거되고, 모달 취소는 저장하지 않는다', async ({ page }) => {
    await open(page);
    await clickRegion(page, JONGNO);
    await expect(page.locator('#ci-xp-total')).toHaveText('+35'); // 일반 10 + 서울 첫 방문 15 + 선점 10
    await page.click('#ci-cancel');
    await expect(page.locator('#checkin')).toBeHidden();
    await expect(region(page, JONGNO)).not.toHaveClass(/\bon\b/);
    await expect(page.locator('#s-cnt')).toHaveText('0 / 250');

    await clickRegion(page, JONGNO);
    await page.click('#ci-save');
    await expect(region(page, JONGNO)).toHaveClass(/\bon\b/);
    await clickRegion(page, JONGNO); // 칠하기 모드 재클릭 = 제거
    await expect(region(page, JONGNO)).not.toHaveClass(/\bon\b/);
    await expect(page.locator('#s-cnt')).toHaveText('0 / 250');
  });

  test('미래 날짜 체크인은 서버 오류를 toast로 보여주고 칠하지 않는다', async ({ page }) => {
    await open(page);
    await clickRegion(page, JONGNO);
    await expect(page.locator('#checkin')).toBeVisible();
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 2);
    await page.fill('#ci-date', localISO(tomorrow));
    await page.click('#ci-save');
    await expect(toast(page, '날짜를 확인해 주세요')).toBeVisible();
    await expect(toast(page, '이후로 적을 수 없어요')).toBeVisible();
    await expect(page.locator('#checkin')).toBeVisible(); // 모달은 열린 채로 고칠 수 있다
    await expect(region(page, JONGNO)).not.toHaveClass(/\bon\b/);
    await expect(page.locator('#s-cnt')).toHaveText('0 / 250');
  });

  test('온보딩(72h)이 끝난 탐험가의 6번째 체크인은 하루 상한 toast', async ({ page, dev }) => {
    await open(page);
    const explorerId = await dev.explorerOf(page);
    await dev.age(explorerId, 73);
    const today = localISO(new Date());
    for (const code of ['KR-11010', 'KR-11020', 'KR-11030', 'KR-11040', 'KR-11050']) {
      await dev.checkIn(explorerId, code, today);
    }
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    await expect(page.locator('#s-cnt')).toHaveText('5 / 250');

    await clickRegion(page, GAPYEONG);
    await expect(page.locator('#ci-xp-total')).toHaveText('+45');
    await page.click('#ci-save');
    await expect(toast(page, '오늘은 여기까지')).toBeVisible();
    await expect(toast(page, '하루 체크인 상한(5곳)')).toBeVisible();
    await expect(region(page, GAPYEONG)).not.toHaveClass(/\bon\b/);
    await expect(page.locator('#s-cnt')).toHaveText('5 / 250');
  });

  test('가입 직후(온보딩 중)에는 하루 5곳을 넘겨 칠할 수 있다', async ({ page, dev }) => {
    await open(page);
    const explorerId = await dev.explorerOf(page);
    const today = localISO(new Date());
    for (const code of ['KR-11010', 'KR-11020', 'KR-11030', 'KR-11040', 'KR-11050']) {
      await dev.checkIn(explorerId, code, today);
    }
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    await clickRegion(page, GAPYEONG);
    await page.click('#ci-save');
    await expect(region(page, GAPYEONG)).toHaveClass(/\bon\b/);
    await expect(page.locator('#s-cnt')).toHaveText('6 / 250');
  });

  test('예시 다시 채우기(POST /dev/seed)와 전부 지우기(DELETE /dev/visits)', async ({ page }) => {
    await open(page);
    await page.click('#t-sample');
    await expect(page.locator('#s-cnt')).toHaveText('45 / 250');
    await expect(page.locator('#s-pct')).toHaveText('18');
    await expect(region(page, JONGNO)).toHaveClass(/\bon\b/);
    await expect(page.locator('#note-txt')).toContainText('예시 데이터');
    await expect(page.locator('#log li').first()).toContainText('뚝섬 한강'); // 일지는 최근 40건, 방문일 최근 순
    await page.locator('#tabs [data-tab="bag"]').click();
    // 3단계: 가방은 서버 값 — 샘플 지역 아이템 45 + 샘플이 완성하는 세트의 배경 1(이벤트로 비동기 반영)
    await expect(page.locator('#n-bag')).toHaveText('46', { timeout: 10_000 });
    await page.locator('#tabs [data-tab="map"]').click();

    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    await expect(page.locator('#s-cnt')).toHaveText('45 / 250');

    await page.click('#t-clear');
    await expect(page.locator('#s-cnt')).toHaveText('0 / 250');
    await expect(region(page, JONGNO)).not.toHaveClass(/\bon\b/);
    await expect(page.locator('#log')).toContainText('아직 기록이 없어요');
  });

  test('서버 초기화로 저장된 탐험가가 사라지면 새로 발급받는다', async ({ page, dev }) => {
    await open(page);
    const first = await dev.explorerOf(page);
    await dev.reset();
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    const second = await dev.explorerOf(page);
    expect(second).not.toBe(first);
    await expect(page.locator('#s-cnt')).toHaveText('0 / 250');
  });
});
