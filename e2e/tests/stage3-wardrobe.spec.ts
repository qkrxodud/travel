import { test, expect } from '../fixtures';
import type { Page } from '@playwright/test';

/**
 * 3단계 파트 B(꾸미기) — 가방 탭이 서버 값(GET /inventory·/scene)에 연결됐는지 확인한다.
 * 체크인 → outbox 릴레이(비동기) → 가방(지역 아이템 지급) → ItemGranted → 장면(자동 착용) 이므로 기대값은 넉넉히 기다린다.
 * 직접 착용·해제·성별·즐겨찾기는 PUT /scene · PUT /inventory/{id}/favorite, 꾸미기 점수는 서버 계산(희귀도 점수 합).
 */

const JONGNO = '11010';           // 청사초롱 등불 — 손(HAND) 일반 1점
const GURYE = '36330';            // 구례 산수유 비니 — 모자(HAT) 희귀 3점
// 지리산 둘레 세트: 남원·구례·하동·산청·함양 → 세트 배경 set:jiri(전설 8점)
const JIRI = ['35050', '36330', '38360', '38370', '38380'];
const LATE = { timeout: 10_000 };

const region = (page: Page, code: string) => page.locator(`path.region[data-code="${code}"]`);
const toast = (page: Page, text: string | RegExp) => page.locator('.toast', { hasText: text });
const tab = (page: Page, name: string) => page.locator(`#tabs [data-tab="${name}"]`).click();
const itemTile = (page: Page, itemId: string) => page.locator(`#bag-inv [data-equip="${itemId}"]`);

function localISO(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** 콘솔 error·pageerror·5xx 응답을 모은다. favicon 404 같은 4xx 는 제외. */
function watchErrors(page: Page) {
  const errors: string[] = [];
  page.on('console', message => { if (message.type() === 'error' && !message.text().includes('favicon')) errors.push(message.text()); });
  page.on('pageerror', error => errors.push(String(error)));
  page.on('response', response => { if (response.status() >= 500) errors.push(`${response.status()} ${response.url()}`); });
  return errors;
}

async function open(page: Page) {
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
  await expect(page.locator('html')).toHaveAttribute('data-wardrobe', /\d+/);
}

async function paint(page: Page, code: string) {
  await tab(page, 'map');
  await region(page, code).dispatchEvent('click');
  await expect(page.locator('#checkin')).toBeVisible();
  await page.click('#ci-save');
  await expect(page.locator('#checkin')).toBeHidden();
  await expect(region(page, code)).toHaveClass(/\bon\b/);
}

async function erase(page: Page, code: string) {
  await tab(page, 'map');
  await region(page, code).dispatchEvent('click'); // 칠하기 모드 재클릭 = 체크인 취소
  await expect(region(page, code)).not.toHaveClass(/\bon\b/);
}

test.describe('가방과 캐릭터 꾸미기', () => {
  test('칠한 지역의 아이템이 가방에 들어와 저절로 입혀지고, 직접 벗기고 입히고 성별·즐겨찾기를 바꿀 수 있으며, 칠한 곳을 지우면 아이템도 돌아가고, 세트를 완성하면 세트 배경을 받는다', async ({ page, dev }) => {
    const errors = watchErrors(page);
    await open(page);

    // 빈 가방
    await tab(page, 'bag');
    await expect(page.locator('#bag-style')).toHaveText('꾸미기 0점 · 맨몸');
    await expect(page.locator('#bag-inv .empty')).toBeVisible();

    // 체크인 2곳(화면) → 가방에 지역 아이템, 빈 슬롯이라 자동 착용, 점수 1 + 3
    await paint(page, JONGNO);
    await paint(page, GURYE);
    await tab(page, 'bag');
    await expect(itemTile(page, 'region:KR-11010')).toHaveClass(/\bon\b/, LATE);
    await expect(itemTile(page, 'region:KR-36330')).toHaveClass(/\bon\b/, LATE);
    await expect(page.locator('#bag-style')).toHaveText('꾸미기 4점 · 소박함', LATE);
    await expect(page.locator('#n-bag')).toHaveText('2');
    await expect(page.locator('#bag-stats')).toContainText('2 착용');

    // 직접 해제(가방 타일 다시 누르기) → 점수 3, 다시 착용 → 4
    await itemTile(page, 'region:KR-11010').click();
    await expect(itemTile(page, 'region:KR-11010')).not.toHaveClass(/\bon\b/);
    await expect(page.locator('#bag-style')).toHaveText('꾸미기 3점 · 소박함');
    await itemTile(page, 'region:KR-11010').click();
    await expect(itemTile(page, 'region:KR-11010')).toHaveClass(/\bon\b/);
    await expect(page.locator('#bag-style')).toHaveText('꾸미기 4점 · 소박함');

    // 슬롯 칸(왼쪽 모자)에서 벗기기
    await page.locator('#slots-left [data-unequip="hat"]').click();
    await expect(itemTile(page, 'region:KR-36330')).not.toHaveClass(/\bon\b/);
    await expect(page.locator('#bag-style')).toHaveText('꾸미기 1점 · 소박함');
    await itemTile(page, 'region:KR-36330').click();
    await expect(page.locator('#bag-style')).toHaveText('꾸미기 4점 · 소박함');

    // 성별(서버 저장 — 새로고침해도 유지)
    await page.locator('[data-gender="f"]').click();
    await expect(page.locator('[data-gender="f"]')).toHaveAttribute('aria-pressed', 'true');
    // 즐겨찾기
    await itemTile(page, 'region:KR-11010').locator('[data-fav]').click();
    await expect(itemTile(page, 'region:KR-11010').locator('[data-fav]')).toHaveText('★');
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-wardrobe', /\d+/);
    await tab(page, 'bag');
    await expect(page.locator('[data-gender="f"]')).toHaveAttribute('aria-pressed', 'true');
    await expect(itemTile(page, 'region:KR-11010').locator('[data-fav]')).toHaveText('★');
    await expect(itemTile(page, 'region:KR-11010')).toHaveClass(/\bon\b/);

    // 체크인 취소 → 지역 아이템 회수 + 장면에서 벗김
    await erase(page, JONGNO);
    await tab(page, 'bag');
    await expect(itemTile(page, 'region:KR-11010')).toHaveCount(0, LATE);
    await expect(page.locator('#bag-style')).toHaveText('꾸미기 3점 · 소박함', LATE);
    await expect(page.locator('#n-bag')).toHaveText('1');

    // 지리산 세트: 3곳은 API, 마지막 1곳은 화면 → 세트 배경(SET_REWARD) 지급 + 빈 배경 슬롯 자동 착용(전설 8점)
    const me = await dev.explorerOf(page);
    const today = localISO(new Date());
    for (const code of JIRI.slice(0, 4).filter(code => code !== GURYE)) await dev.checkIn(me, 'KR-' + code, today);
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-wardrobe', /\d+/);
    await paint(page, JIRI[4]);
    await expect(toast(page, '세트 배경 해금')).toBeVisible(LATE);
    await tab(page, 'bag');
    const background = itemTile(page, 'set:jiri');
    await expect(background).toHaveClass(/\bset\b/, LATE);
    await expect(background).toHaveClass(/\bon\b/, LATE);
    await expect(page.locator('#slots-right [data-unequip="bg"]')).toBeVisible();
    await expect(page.locator('#bag-stats')).toContainText('1 세트 배경');

    // 세트 보상은 취소해도 남는다(취소 비대칭)
    await erase(page, JIRI[4]);
    await tab(page, 'bag');
    await expect(itemTile(page, 'region:KR-38380')).toHaveCount(0, LATE);
    await expect(itemTile(page, 'set:jiri')).toHaveCount(1);

    expect(errors, errors.join('\n')).toEqual([]);
  });
});
