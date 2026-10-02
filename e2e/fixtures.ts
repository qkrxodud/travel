import { test as base, expect, APIRequestContext } from '@playwright/test';

/**
 * 공통 fixture.
 * - 모든 테스트 전에 DELETE /dev/reset 으로 서버 상태를 초기화한다(local 프로파일 전용 엔드포인트).
 * - seed / login 헬퍼는 이후 단계에서 실제 동작이 붙는 자리다.
 */
export type DevApi = {
  /** 전체 데이터 초기화 (DELETE /dev/reset) */
  reset: () => Promise<void>;
  /** 테스트 시드 투입 (POST /dev/seed). 이후 단계에서 시나리오 이름 등을 받도록 확장한다. */
  seed: (body?: unknown) => Promise<void>;
  /** 개발용 로그인 (POST /dev/login). 이후 단계에서 구현 예정 — 지금 호출하면 에러를 던진다. */
  login: (userId?: string) => Promise<void>;
};

async function expectOk(res: Awaited<ReturnType<APIRequestContext['get']>>, what: string) {
  expect(res.ok(), `${what} -> HTTP ${res.status()}`).toBeTruthy();
}

export const test = base.extend<{ dev: DevApi }>({
  dev: [
    async ({ request }, use) => {
      const dev: DevApi = {
        reset: async () => expectOk(await request.delete('/dev/reset'), 'DELETE /dev/reset'),
        seed: async (body?: unknown) =>
          expectOk(await request.post('/dev/seed', body === undefined ? {} : { data: body }), 'POST /dev/seed'),
        login: async () => {
          throw new Error('dev.login 은 아직 구현되지 않았다 (이후 단계에서 /dev/login 연결)');
        },
      };
      await dev.reset();
      await use(dev);
    },
    { auto: true },
  ],
});

export { expect };
