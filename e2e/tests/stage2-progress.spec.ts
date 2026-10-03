import { test, expect, EXPLORER_HEADER } from '../fixtures';
import type { Page } from '@playwright/test';

/**
 * 2단계(진행) — 헤더(Lv·XP·스트릭·칭호)·도감·퀘스트·프로필(칭호·뱃지)이 서버 진행 값에 연결됐는지 확인한다.
 * 체크인/취소 → outbox 릴레이(비동기) → 진행 반영이므로, 화면은 변경 직후 짧게 재조회한다(그래서 기대값은 넉넉히 기다린다).
 */

// 지리산 둘레 세트: 남원시(일반)·구례군·하동군·산청군·함양군(희귀) — 전북·전남·경남
const NAMWON = '35050', GURYE = '36330', HADONG = '38360', SANCHEONG = '38370', HAMYANG = '38380';
const LATE = { timeout: 10_000 };

const region = (page: Page, code: string) => page.locator(`path.region[data-code="${code}"]`);
const toast = (page: Page, text: string | RegExp) => page.locator('.toast', { hasText: text });
const tab = (page: Page, name: string) => page.locator(`#tabs [data-tab="${name}"]`).click();

function localISO(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** 콘솔 error·pageerror·5xx 응답을 모은다(QA P3-10). favicon 404 같은 4xx 는 제외. */
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
  await expect(page.locator('html')).toHaveAttribute('data-progress', /\d+/);
}

async function paint(page: Page, code: string, xpTotal?: string) {
  await region(page, code).dispatchEvent('click');
  await expect(page.locator('#checkin')).toBeVisible();
  if (xpTotal) await expect(page.locator('#ci-xp-total')).toHaveText(xpTotal);
  await page.click('#ci-save');
  await expect(page.locator('#checkin')).toBeHidden();
  await expect(region(page, code)).toHaveClass(/\bon\b/);
}

test.describe('XP와 레벨, 도감, 퀘스트', () => {
  test('칠할수록 XP와 레벨이 오르고 세트를 완성하면 칭호를 받으며, 퀘스트 보상은 한 번만 받고, 칠한 곳을 지우면 XP만 줄고 완성한 세트는 남으며, 받은 칭호 가운데 하나를 골라 단다', async ({ page, dev, request }) => {
    const errors = watchErrors(page);
    await open(page);
    await expect(page.locator('#lv')).toHaveText('Lv.1');
    await expect(page.locator('#s-xp')).toHaveText('0');
    await expect(page.locator('#ttl')).toHaveText('초보 탐험가');
    await expect(page.locator('#s-next')).toHaveText('다음 레벨까지 40');

    // 첫 체크인(화면) → 헤더 XP 가 서버 값으로 오른다(일반 10 + 전북 첫 발 15 + 선점 10)
    await paint(page, NAMWON, '+35');
    await expect(page.locator('#s-xp')).toHaveText('35', LATE);
    await expect(page.locator('#s-streak')).toHaveText('1', LATE);
    const me = await dev.explorerOf(page);

    // 세트 지역 3곳은 API 로, 마지막 1곳은 화면으로 → 세트 완성(+100)
    const today = localISO(new Date());
    for (const code of [GURYE, HADONG, SANCHEONG]) await dev.checkIn(me, 'KR-' + code, today);
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    await paint(page, HAMYANG, '+30');
    await expect(page.locator('#s-xp')).toHaveText('285', LATE); // 35+45+45+30+30 + 세트 100
    await expect(page.locator('#lv')).toHaveText('Lv.4');
    await expect(page.locator('#ttl')).toHaveText('동네 산책러');

    // 도감 탭: 지도 기준 세트 완성
    await tab(page, 'sets');
    const jiri = page.locator('.set[data-set="jiri"]');
    await expect(jiri).toHaveClass(/\bdone\b/);
    await expect(jiri.locator('.have')).toHaveText('5 / 5');
    await expect(jiri).toContainText('완성 · 칭호 「산 사람」 +100 XP');
    await expect(page.locator('#sets-sum')).toHaveText('1 / 9 완성');
    await expect(page.locator('#n-sets')).toHaveText('1');

    // 퀘스트 탭: 이번 달 4개 모두 달성 → m3 보상 받기 1회
    await tab(page, 'quests');
    await expect(page.locator('#q-month')).toContainText('4/4 완료');
    const m3 = page.locator('.quest[data-quest="m3"]');
    await expect(m3.locator('[data-claim="m3"]')).toBeVisible();
    await m3.locator('[data-claim="m3"]').click();
    await expect(toast(page, '퀘스트 보상')).toBeVisible();
    await expect(m3.locator('.st')).toHaveText('받음');
    await expect(m3.locator('[data-claim]')).toHaveCount(0);
    await expect(page.locator('#s-xp')).toHaveText('345', LATE); // +60
    const again = await request.post('/quests/m3/claim', { headers: { [EXPLORER_HEADER]: me } });
    expect(again.status()).toBe(409);
    expect((await again.json()).code).toBe('QUEST_ALREADY_CLAIMED');
    await expect(page.locator('.quest[data-quest="gun30"] .st')).toHaveText('4/30');

    // 지도: 함양군 취소 → 기본 XP 20 만 감소, 도감 완성 기록 유지(4/5)
    await tab(page, 'map');
    await region(page, HAMYANG).dispatchEvent('click');
    await expect(region(page, HAMYANG)).not.toHaveClass(/\bon\b/);
    await expect(page.locator('#s-xp')).toHaveText('325', LATE);
    await tab(page, 'sets');
    await expect(jiri.locator('.have')).toHaveText('4 / 5', LATE);
    await expect(jiri).toHaveClass(/\bdone\b/);
    await tab(page, 'quests');
    await expect(m3.locator('.st')).toHaveText('받음');

    // 프로필: 뱃지(첫 발자국·첫 세트) + 칭호 선택(얻은 것만) → 헤더 칭호, 새로고침 후에도 유지(서버)
    await tab(page, 'profile');
    await expect(page.locator('#b-cnt')).toHaveText('2 / 12');
    await expect(page.locator('.badge[data-badge="first"]')).toHaveClass(/\bgot\b/);
    await expect(page.locator('.badge[data-badge="set1"]')).toHaveClass(/\bgot\b/);
    await expect(page.locator('.badge[data-badge="ten"]')).not.toHaveClass(/\bgot\b/);
    await expect(page.locator('[data-title="own-KR-11"]')).toBeDisabled();
    await page.locator('[data-title="set-jiri"]').click();
    await expect(toast(page, '칭호 변경')).toBeVisible();
    await expect(page.locator('#ttl')).toHaveText('산 사람');
    await expect(page.locator('[data-title="set-jiri"]')).toHaveAttribute('aria-pressed', 'true');
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-progress', /\d+/);
    await expect(page.locator('#ttl')).toHaveText('산 사람');
    const progress = await dev.progress(me);
    expect(progress.selectedTitleId).toBe('set-jiri');
    expect(progress.xp).toBe(325);

    // 회귀(QA P3-10): 가방·랭킹 탭이 서버 진행 값과 함께 정상 렌더링
    await tab(page, 'bag');
    await expect(page.locator('#n-bag')).toHaveText('5', LATE); // 함양군 취소 후 지역 아이템 4 + 세트 배경 set:jiri(3단계 서버 가방, 회수 없음)
    await expect(page.locator('#bag-inv .item')).not.toHaveCount(0);
    await tab(page, 'rank');
    const meRow = page.locator('#rank li.me');
    await expect(meRow).toContainText('나 (Kobi)');
    await expect(meRow).toContainText('산 사람'); // 서버 선택 칭호
    await expect(meRow.locator('.sc')).toContainText('4');
    expect(errors, errors.join('\n')).toEqual([]);
  });

  test('예시 영토는 예시 날짜에 칠한 것으로 쳐서 스트릭·월간 퀘스트·뱃지가 채워지고, 전부 지우면 진행도 비워진다', async ({ page }) => {
    const errors = watchErrors(page);
    await open(page);
    await page.click('#t-sample');
    await expect(page.locator('#s-cnt')).toHaveText('45 / 250');
    await expect(page.locator('#s-streak')).toHaveText('20', LATE); // 19개월 전 ~ 이번 달 매달 1곳 이상
    await expect(page.locator('#lv')).toHaveText('Lv.8', LATE);
    await tab(page, 'quests');
    await expect(page.locator('.quest[data-quest="m3"] .st')).toHaveText('1/3', LATE); // 이번 달은 뚝섬 1곳
    await expect(page.locator('#streak span.on')).toHaveCount(12);
    await tab(page, 'sets');
    await expect(page.locator('.set[data-set="sea"]')).toHaveClass(/\bdone\b/, LATE);
    await tab(page, 'profile');
    await expect(page.locator('#b-cnt')).toHaveText('6 / 12', LATE);
    await expect(page.locator('.badge[data-badge="streak3"]')).toHaveClass(/\bgot\b/);

    // 전부 지우기 → 진행도 비워진다(dev 전용, 프로토타입 clear 와 같게)
    await tab(page, 'map');
    await page.click('#t-clear');
    await expect(page.locator('#s-cnt')).toHaveText('0 / 250');
    await expect(page.locator('#s-xp')).toHaveText('0', LATE);
    await expect(page.locator('#lv')).toHaveText('Lv.1');
    for (const name of ['bag', 'sets', 'quests', 'rank', 'profile', 'map']) await tab(page, name);
    expect(errors, errors.join('\n')).toEqual([]);
  });
});
