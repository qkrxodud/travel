import { defineConfig, devices } from '@playwright/test';

/**
 * 기본은 http://localhost:8080 (스펙 기준).
 * 8080을 다른 프로세스가 점유하고 있으면 E2E_PORT 로 바꿔 실행한다.
 *   E2E_PORT=18080 npx playwright test
 * 이 경우 bootRun 에 --server.port 를 넘긴다.
 */
const PORT = process.env.E2E_PORT ?? '8080';
const BASE_URL = `http://localhost:${PORT}`;
/**
 * 13s단계: 계절 명소 TourAPI 는 실제 키 대신 서버 자신의 가짜 TourAPI(/dev/tourapi, local 전용)를 부르게 띄운다 —
 * 실제 키·실제 호출 없이 "키 있는 흐름"(후보 → 확정 → 근거 표시)을 확인한다. 키 값은 아무 문자열(dev-fixture-key).
 */
const FAKE_TOURAPI = `--territory.tourapi.service-key=dev-fixture-key --territory.tourapi.base-url=${BASE_URL}/dev/tourapi`;
const BOOT_RUN = `./gradlew :app-api:bootRun --args='--server.port=${PORT} ${FAKE_TOURAPI}'`;

export default defineConfig({
  testDir: './tests',
  // webServer 기동(또는 재사용) 후 /health 의 service==='territory' 를 확인한다(남의 서버 재사용 방지).
  globalSetup: './global-setup.ts',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: BOOT_RUN,
    cwd: '..',
    url: `${BASE_URL}/health`,
    reuseExistingServer: true,
    timeout: 180000,
    stdout: 'ignore',
    stderr: 'pipe',
  },
});
