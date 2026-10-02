import { test as base, expect, APIRequestContext, Page } from '@playwright/test';

/**
 * 공통 fixture.
 * - 모든 테스트 전에 DELETE /dev/reset 으로 서버 데이터를 비운다(local 프로파일 전용, 전체 테이블).
 * - 1단계부터 방문 기록은 서버가 진실 원천이다. 3단계(결정 2)부터 탐험가는 발급 때 받은 비밀 접근 토큰을
 *   X-Explorer-Token 헤더로 보내 인증한다(explorerId 는 공개 식별자). 아래 dev 함수의 `explorer` 인자는 그 토큰이다.
 */
export const EXPLORER_HEADER = 'X-Explorer-Token';

export type DevApi = {
  /** 전체 데이터 초기화 (DELETE /dev/reset) */
  reset: () => Promise<void>;
  /** 탐험가에 프로토타입 SAMPLE 45곳 시드 (POST /dev/seed) */
  seed: (token: string) => Promise<number>;
  /** 탐험가 가입 시각을 hours 만큼 과거로(온보딩 72h 예외 종료 시뮬레이션) */
  age: (token: string, hours: number) => Promise<void>;
  /** 익명 탐험가 발급 (POST /explorers) — accessToken 이 인증 토큰 */
  newExplorer: () => Promise<{ explorerId: string; personalMapId: string; accessToken: string }>;
  /** 서버 체크인 (POST /visits) — 화면을 거치지 않고 상태를 만들 때. mapId 생략 = 개인 지도 */
  checkIn: (token: string, regionCode: string, visitDate: string, memo?: string, mapId?: string) => Promise<void>;
  /** 화면이 발급·저장한 탐험가 접근 토큰 (localStorage territory-explorer-token) — dev 함수에 그대로 넘긴다 */
  explorerOf: (page: Page) => Promise<string>;
  /** 화면이 발급·저장한 탐험가 id(공개 식별자, localStorage territory-explorer-id) */
  idOf: (page: Page) => Promise<string>;
  /** 진행 조회 (GET /progress) — 2단계 */
  progress: (token: string) => Promise<any>;
  /** 개발용 로그인 (POST /dev/login). 4단계 구현 예정 — 지금 호출하면 에러를 던진다. */
  login: (userId?: string) => Promise<void>;
};

async function expectOk(res: Awaited<ReturnType<APIRequestContext['get']>>, what: string) {
  expect(res.ok(), `${what} -> HTTP ${res.status()} ${await res.text()}`).toBeTruthy();
}

export const test = base.extend<{ dev: DevApi }>({
  dev: [
    async ({ request }, use) => {
      const dev: DevApi = {
        reset: async () => expectOk(await request.delete('/dev/reset'), 'DELETE /dev/reset'),
        seed: async (explorerId) => {
          const res = await request.post('/dev/seed', { headers: { [EXPLORER_HEADER]: explorerId } });
          await expectOk(res, 'POST /dev/seed');
          return (await res.json()).seeded;
        },
        age: async (explorerId, hours) =>
          expectOk(await request.post('/dev/explorers/age', { headers: { [EXPLORER_HEADER]: explorerId }, data: { hours } }),
            'POST /dev/explorers/age'),
        newExplorer: async () => {
          const res = await request.post('/explorers');
          await expectOk(res, 'POST /explorers');
          return res.json();
        },
        checkIn: async (explorerId, regionCode, visitDate, memo = '', mapId) =>
          expectOk(await request.post('/visits', {
            headers: { [EXPLORER_HEADER]: explorerId },
            data: { regionCode, visitDate, memo, ...(mapId ? { mapId } : {}) },
          }), `POST /visits ${regionCode}`),
        explorerOf: async (page) => {
          const token = await page.evaluate(() => localStorage.getItem('territory-explorer-token'));
          expect(token, 'localStorage territory-explorer-token').toBeTruthy();
          return token!;
        },
        idOf: async (page) => {
          const id = await page.evaluate(() => localStorage.getItem('territory-explorer-id'));
          expect(id, 'localStorage territory-explorer-id').toBeTruthy();
          return id!;
        },
        progress: async (explorerId) => {
          const res = await request.get('/progress', { headers: { [EXPLORER_HEADER]: explorerId } });
          await expectOk(res, 'GET /progress');
          return res.json();
        },
        login: async () => {
          throw new Error('dev.login 은 아직 구현되지 않았다 (4단계에서 /dev/login 연결)');
        },
      };
      await dev.reset();
      await use(dev);
    },
    { auto: true },
  ],
});

export { expect };
