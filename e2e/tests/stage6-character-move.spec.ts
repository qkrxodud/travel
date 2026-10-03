import { test, expect } from '../fixtures';
import type { Page } from '@playwright/test';

/**
 * 6단계(React 이전) 성능 회귀 감시 — 체크인 후 캐릭터 이동(0.9초 깡충)이 끊기지 않는지 프레임 샘플링으로 잰다.
 * 체크인 직후에는 반영 대기 창(진행·가방 재조회)이 열려 화면이 여러 번 다시 렌더되는데, 그때 이동 애니메이션을
 * 처음부터 다시 걸면 캐릭터가 출발점으로 되돌아간다(2026-10-03 수정 건: 역행 3회, 이동 프레임 146).
 * 기준: 이동 중 역행 0회 · 한 번의 이동(프레임 수가 0.9초 1회 분량) · 목적지 도착.
 */

const JONGNO = '11010';     // 서울 종로구 — 출발(API 로 먼저 체크인)
const HAEUNDAE = '21090';   // 부산 해운대구 — 도착(화면에서 체크인)
const MOKPO = '36010';      // 전남 목포시 — 이동 도중 두 번째 목적지(서쪽 — 출발점 종로로 되돌아가면 크게 튄다)
const SAMPLE_MS = 3000;     // 이동 0.9초 + 반영 대기 창(재조회) 대부분을 덮는다
/**
 * 한 프레임 이동량 상한(px). 정상 이동은 0.9초 cubic 이라 프레임당 최대 20~40px(헤드리스 프레임 지연 포함).
 * 이동 도중 새 목적지에서 첫 출발 지역부터 다시 시작하면 그 거리만큼 한 번에 튄다 — 이 스펙은 종로에서 90px 넘게
 * 떠난 뒤 두 번째 체크인을 하므로 예전 엔진이면 90px 이상 튄다.
 */
const MAX_FRAME_STEP = 50;

const region = (page: Page, code: string) => page.locator(`path.region[data-code="${code}"]`);

function localISO(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

type Sample = { at: number; x: number; y: number };

/** 지금부터 duration 동안 매 프레임 캐릭터 위치를 window.__charSamples 에 적는다 */
async function startSampling(page: Page, duration: number) {
  await page.evaluate((ms) => {
    const samples: { at: number; x: number; y: number }[] = [];
    const scope = window as unknown as { __charSamples: typeof samples; __sampleBegan: number };
    scope.__charSamples = samples;
    const node = document.querySelector('#map g.charpos');
    const began = performance.now();
    scope.__sampleBegan = began;
    const tick = (now: number) => {
      const match = /translate\(([-\d.e]+),\s*([-\d.e]+)\)/.exec(node?.getAttribute('transform') ?? '');
      if (match) samples.push({ at: now - began, x: Number(match[1]), y: Number(match[2]) });
      if (now - began < ms) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  }, duration);
}

async function readyWithJongno(page: Page, dev: { explorerOf: (page: Page) => Promise<string>; checkIn: (token: string, regionCode: string, visitDate: string) => Promise<void> }) {
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
  const token = await dev.explorerOf(page);
  await dev.checkIn(token, 'KR-' + JONGNO, localISO(new Date()));
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
  await expect(page.locator('html')).toHaveAttribute('data-wardrobe', /\d+/);
  const charpos = page.locator('#map g.charpos');
  await expect(charpos).toHaveAttribute('transform', /translate\(/);
  const start = await charpos.getAttribute('transform');
  const [startX, startY] = /translate\(([-\d.e]+),\s*([-\d.e]+)\)/.exec(start ?? '')!.slice(1).map(Number);
  return { startX, startY };
}

test.describe('6단계 캐릭터 이동 프레임', () => {
  test('체크인 후 이동 중 역행 0회 — 재조회·재렌더가 애니메이션을 다시 걸지 않는다', async ({ page, dev }) => {
    await page.goto('/');
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    const token = await dev.explorerOf(page);
    await dev.checkIn(token, 'KR-' + JONGNO, localISO(new Date()));
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-territory', 'ready');
    await expect(page.locator('html')).toHaveAttribute('data-wardrobe', /\d+/);
    const charpos = page.locator('#map g.charpos');
    await expect(charpos).toHaveAttribute('transform', /translate\(/);
    const start = await charpos.getAttribute('transform');

    // 저장 버튼을 누르기 직전부터 매 프레임 캐릭터 위치를 적는다
    await region(page, HAEUNDAE).dispatchEvent('click');
    await expect(page.locator('#checkin')).toBeVisible();
    await page.evaluate((duration) => {
      const samples: { at: number; x: number; y: number }[] = [];
      (window as unknown as { __charSamples: typeof samples }).__charSamples = samples;
      const node = document.querySelector('#map g.charpos');
      const began = performance.now();
      const tick = (now: number) => {
        const match = /translate\(([-\d.e]+),\s*([-\d.e]+)\)/.exec(node?.getAttribute('transform') ?? '');
        if (match) samples.push({ at: now - began, x: Number(match[1]), y: Number(match[2]) });
        if (now - began < duration) requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    }, SAMPLE_MS);
    await page.click('#ci-save');
    await expect(page.locator('#checkin')).toBeHidden();
    await expect(region(page, HAEUNDAE)).toHaveClass(/\bon\b/);
    await page.waitForTimeout(SAMPLE_MS + 300);

    const samples = await page.evaluate(() => (window as unknown as { __charSamples: Sample[] }).__charSamples);
    const [startX] = /translate\(([-\d.e]+),/.exec(start ?? '')!.slice(1).map(Number);
    const end = samples[samples.length - 1];
    expect(samples.length, '샘플 프레임').toBeGreaterThan(30);
    expect(Math.abs(end.x - startX), '부산까지 이동했다').toBeGreaterThan(50);

    // x 는 출발 → 도착으로 선형 보간된다(y 는 깡충 호) — 진행률 t = (x - 출발) / (도착 - 출발)
    const progress = samples.map(sample => (sample.x - startX) / (end.x - startX));
    let regressions = 0;
    for (let i = 1; i < progress.length; i++) if (progress[i] < progress[i - 1] - 1e-6) regressions++;
    const movingFrames = progress.filter(value => value > 1e-6 && value < 1 - 1e-6).length;
    console.log(`캐릭터 이동 샘플 ${samples.length}프레임 · 이동 중 ${movingFrames}프레임 · 역행 ${regressions}회`);

    expect(regressions, '이동 중 역행(애니메이션 재시작)').toBe(0);
    expect(movingFrames, '이동은 0.9초 한 번(재시작하면 프레임이 늘어난다)').toBeGreaterThan(5);
    expect(movingFrames, '이동은 0.9초 한 번(재시작하면 프레임이 늘어난다)').toBeLessThan(90);
    expect(progress[progress.length - 1]).toBeCloseTo(1, 5);
  });

  test('이동 도중 두 번째 체크인 — 지금 위치에서 이어서 출발한다(한 프레임 순간이동 없음)', async ({ page, dev }) => {
    const { startX, startY } = await readyWithJongno(page, dev);

    await region(page, HAEUNDAE).dispatchEvent('click');
    await expect(page.locator('#checkin')).toBeVisible();
    await startSampling(page, SAMPLE_MS + 500);
    await page.click('#ci-save');
    await expect(page.locator('#checkin')).toBeHidden();
    // 해운대로 가는 도중(0.9초 안)에 목포를 체크인한다 — 모달을 먼저 열어 두고, 캐릭터가 종로를 확실히 떠난 뒤 저장
    await region(page, MOKPO).dispatchEvent('click');
    await expect(page.locator('#checkin')).toBeVisible();
    await page.waitForFunction(([fromX, fromY]) => {
      const match = /translate\(([-\d.e]+),\s*([-\d.e]+)\)/.exec(document.querySelector('#map g.charpos')?.getAttribute('transform') ?? '');
      return !!match && Math.hypot(Number(match[1]) - fromX, Number(match[2]) - fromY) > 90;
    }, [startX, startY], { polling: 'raf', timeout: 2000 });
    const secondAt = await page.evaluate(() => performance.now() - (window as unknown as { __sampleBegan: number }).__sampleBegan);
    await page.click('#ci-save');
    await expect(page.locator('#checkin')).toBeHidden();
    await expect(region(page, MOKPO)).toHaveClass(/\bon\b/);
    await page.waitForTimeout(SAMPLE_MS + 800);

    const samples = await page.evaluate(() => (window as unknown as { __charSamples: Sample[] }).__charSamples);
    expect(samples.length, '샘플 프레임').toBeGreaterThan(30);
    const steps = samples.slice(1).map((sample, i) => Math.hypot(sample.x - samples[i].x, sample.y - samples[i].y));
    const maxStep = Math.max(...steps);
    // 두 번째 체크인을 누른 순간 캐릭터는 종로를 떠나 있어야 이 시나리오가 의미 있다(아직 출발 전이면 이어 갈 위치가 없다)
    const beforeSecond = samples.filter(sample => sample.at <= secondAt).pop();
    const leftJongno = beforeSecond ? Math.hypot(beforeSecond.x - startX, beforeSecond.y - startY) : 0;
    const end = samples[samples.length - 1];
    console.log(`이동 중 두 번째 체크인: 샘플 ${samples.length}프레임 · 최대 한 프레임 ${maxStep.toFixed(1)}px · 두 번째 체크인 때 종로에서 ${leftJongno.toFixed(1)}px`);

    expect(leftJongno, '두 번째 체크인 때 이미 해운대로 이동 중').toBeGreaterThan(60);
    expect(maxStep, '한 프레임 이동량(순간이동 없음)').toBeLessThan(MAX_FRAME_STEP);
    // 목포(서쪽)에 도착 — 해운대(동쪽)보다 왼쪽, 종로에서도 멀리
    expect(end.x, '목포에 도착').toBeLessThan(startX);
    expect(samples.slice(-5).every(sample => sample.x === end.x && sample.y === end.y), '도착 뒤 멈춤').toBe(true);
  });
});

