import { test as base, expect, APIRequestContext, Page } from '@playwright/test';

/**
 * 공통 fixture.
 * - 모든 테스트 전에 DELETE /dev/reset 으로 서버 데이터를 비운다(local 프로파일 전용, 전체 테이블).
 * - 1단계부터 방문 기록은 서버가 진실 원천이다. 3단계(결정 2)부터 탐험가는 발급 때 받은 비밀 접근 토큰을
 *   X-Explorer-Token 헤더로 보내 인증한다(explorerId 는 공개 식별자). 아래 dev 함수의 `explorer` 인자는 그 토큰이다.
 */
export const EXPLORER_HEADER = 'X-Explorer-Token';

/** 어떤 스펙도 칠하지 않는 희귀 지역(부산 기장군) — 테스트마다 이번 주 미스터리 지역으로 고정해 XP 기대값을 주차와 무관하게 지킨다 */
export const QUIET_MYSTERY = 'KR-21310';
const SHIFT_DAYS = Number(process.env.E2E_SHIFT_DAYS ?? 0);

export type DevApi = {
  /** 전체 데이터 초기화 (DELETE /dev/reset — 서버 시계·미스터리 고정도 되돌린다) */
  reset: () => Promise<void>;
  /**
   * 이번 주부터 미스터리 지역을 고정(PUT /dev/mystery, 8단계). 주마다 바뀌는 미스터리 지역이 스펙이 칠하는 희귀 지역과 겹쳐
   * +50 이 붙지 않게, 모든 테스트는 시작 때 스펙이 칠하지 않는 지역(기장군)으로 고정해 둔다. null 이면 원래 주차 선택으로.
   */
  pinMystery: (regionCode: string | null) => Promise<void>;
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
  /**
   * 개발용 로그인 (POST /dev/login, 4단계) — 실제 구글 OIDC 성공과 같은 계정 연결·병합 경로. 세션 쿠키는 넘긴 컨텍스트에 실린다:
   * 화면을 로그인시키려면 `page`(= page.request, 브라우저와 쿠키 공유)를 넘긴다. token 을 주면 그 익명 탐험가가 연결·병합 대상.
   */
  login: (target: Page | APIRequestContext, opts: { email: string; sub?: string; token?: string }) => Promise<LoginResult>;
};

/** POST /dev/login 응답(_workspace/04_contracts.md A4). */
export type LoginResult = {
  explorerId: string;
  handle: string;
  email: string;
  personalMapId: string;
  outcome: 'CREATED' | 'LINKED' | 'MERGED' | 'SIGNED_IN';
  merge: { fromExplorerId: string; movedRegions: number; newRegions: number } | null;
};

async function expectOk(res: Awaited<ReturnType<APIRequestContext['get']>>, what: string) {
  expect(res.ok(), `${what} -> HTTP ${res.status()} ${await res.text()}`).toBeTruthy();
}

export const test = base.extend<{ dev: DevApi }>({
  dev: [
    async ({ request }, use) => {
      const dev: DevApi = {
        reset: async () => expectOk(await request.delete('/dev/reset'), 'DELETE /dev/reset'),
        pinMystery: async (regionCode) => expectOk(await request.put('/dev/mystery', { data: { regionCode } }), `PUT /dev/mystery ${regionCode}`),
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
        login: async (target, { email, sub, token }) => {
          const client = 'request' in target ? (target as Page).request : (target as APIRequestContext);
          const res = await client.post('/dev/login', {
            headers: token ? { [EXPLORER_HEADER]: token } : {},
            data: { email, ...(sub ? { sub } : {}) },
          });
          await expectOk(res, 'POST /dev/login');
          return res.json();
        },
      };
      await dev.reset();
      // E2E_SHIFT_DAYS=n: 서버 시계를 n일 앞으로 민 채로 돌린다(주차·달에 기대값이 묶이지 않았는지 확인용)
      if (SHIFT_DAYS) await expectOk(await request.post('/dev/clock', { data: { days: SHIFT_DAYS } }), `POST /dev/clock +${SHIFT_DAYS}d`);
      await dev.pinMystery(QUIET_MYSTERY);
      await use(dev);
    },
    { auto: true },
  ],
});

export { expect };
