import { test, expect } from '../fixtures';

const TABS = ['map', 'bag', 'sets', 'quests', 'rank', 'profile'] as const;

/**
 * 외부 CDN(폰트 등) 로딩 실패처럼 앱과 무관한 콘솔 에러는 무시한다.
 * 같은 origin(우리 서버) 리소스 실패는 무시하지 않는다.
 */
function isExternal(url: string | undefined, baseURL: string): boolean {
  if (!url) return false;
  try {
    return new URL(url).origin !== new URL(baseURL).origin;
  } catch {
    return false;
  }
}

test.describe('0단계 뼈대 부팅', () => {
  test('첫 화면이 로드되고 타이틀이 나의 영토다', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle('나의 영토');
    await expect(page.locator('#tabs')).toBeVisible();
    await expect(page.locator('#tab-map')).toBeVisible();
  });

  test('탭 6개를 클릭하면 해당 탭으로 전환된다', async ({ page }) => {
    await page.goto('/');
    const buttons = page.locator('#tabs [data-tab]');
    await expect(buttons).toHaveCount(TABS.length);

    for (const tab of TABS) {
      await page.locator(`#tabs [data-tab="${tab}"]`).click();
      await expect(page.locator(`#tabs [data-tab="${tab}"]`)).toHaveAttribute('aria-selected', 'true');
      await expect(page.locator(`#tab-${tab}`)).toBeVisible();
      for (const other of TABS.filter((t) => t !== tab)) {
        await expect(page.locator(`#tab-${other}`)).toBeHidden();
        await expect(page.locator(`#tabs [data-tab="${other}"]`)).toHaveAttribute('aria-selected', 'false');
      }
    }
  });

  test('페이지 콘솔 에러가 없다', async ({ page, baseURL }) => {
    const errors: string[] = [];
    const ignored: string[] = [];
    page.on('console', (msg) => {
      if (msg.type() !== 'error') return;
      const text = `${msg.text()} @ ${msg.location().url}`;
      if (isExternal(msg.location().url, baseURL!)) ignored.push(text);
      else errors.push(text);
    });
    page.on('pageerror', (err) => errors.push(`pageerror: ${err.message}`));

    await page.goto('/');
    await page.waitForLoadState('networkidle');
    for (const tab of TABS) {
      await page.locator(`#tabs [data-tab="${tab}"]`).click();
    }

    if (ignored.length) console.log(`외부 리소스 콘솔 에러 ${ignored.length}건 무시:\n${ignored.join('\n')}`);
    expect(errors, errors.join('\n')).toEqual([]);
  });

  test('/health 응답에 dailyCap 5가 있다', async ({ request }) => {
    const res = await request.get('/health');
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.status).toBe('UP');
    expect(body.dailyCap).toBe(5);
  });
});
