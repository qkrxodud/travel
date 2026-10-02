import type { FullConfig } from '@playwright/test';

/**
 * reuseExistingServer 가 같은 포트의 "남의 서버"를 재사용하지 않도록 확인한다.
 * webServer 는 url(/health)이 2xx 이면 기존 서버를 재사용하므로, 여기서 응답이 이 프로젝트(service=territory)인지 본다.
 */
export default async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0]?.use?.baseURL;
  if (!baseURL) throw new Error('baseURL 이 설정되지 않았다');
  let body: any;
  try {
    const res = await fetch(`${baseURL}/health`);
    body = await res.json();
  } catch (e) {
    throw new Error(`${baseURL}/health 응답을 읽을 수 없다 — 다른 프로세스가 포트를 쓰고 있지 않은지 확인: ${e}`);
  }
  if (body?.service !== 'territory') {
    throw new Error(`${baseURL} 는 territory 서버가 아니다(/health=${JSON.stringify(body)}). E2E_PORT 로 다른 포트를 쓰자.`);
  }
}
