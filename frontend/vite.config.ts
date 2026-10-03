import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

/**
 * 개발 서버(npm run dev)가 Spring 서버로 넘길 API 경로 — 이 목록 한 곳만 고친다.
 * 정적 화면(/, /assets/*)만 Vite 가 내고 나머지는 전부 서버 몫이다(세션 쿠키·CSRF 쿠키도 그대로 오간다).
 */
export const API_PATHS = [
  'explorers', 'territory', 'visits', 'maps', 'me', 'progress', 'collection', 'quests',
  'inventory', 'scene', 'friends', 'feed', 'rankings', 'compare', 'catalog',
  'auth', 'logout', 'login', 'oauth2', 'u', 'dev', 'admin', 'health', 'actuator',
] as const;

/**
 * 프록시 대상 Spring 서버. 기본은 http://localhost:18081(bootRun --args='--server.port=18081') — bootRun 기본 포트 8080 은
 * 이 PC 에서 다른 앱이 쓰고 18080 은 운영 compose 자리라 피한다. 다른 포트면 VITE_API_TARGET=http://localhost:18082 npm run dev
 */
export const DEFAULT_API_TARGET = 'http://localhost:18081';
const API_TARGET = process.env.VITE_API_TARGET || DEFAULT_API_TARGET;

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      [`^/(${API_PATHS.join('|')})(/|\\.|\\?|$)`]: { target: API_TARGET, changeOrigin: false },
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    // 픽셀 스프라이트(PNG 6~8KB)는 파일로 낸다(인라인하지 않음 — 캐시되게)
    assetsInlineLimit: 4096,
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    restoreMocks: true,
  },
});
