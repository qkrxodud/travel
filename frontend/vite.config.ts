import { defineConfig } from 'vitest/config';
import type { Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = dirname(fileURLToPath(import.meta.url));
const PWA_DIR = resolve(ROOT, 'src/pwa');
const SW_ENTRY = resolve(ROOT, 'src/sw/serviceWorker.ts');
/** 사이트 루트에 그대로 내는 PWA 파일(빌드 산출물 경로 → 원본) — 매니페스트와 아이콘 */
function pwaFiles(): Record<string, string> {
  const files: Record<string, string> = { 'manifest.json': resolve(PWA_DIR, 'manifest.json') };
  for (const name of readdirSync(resolve(PWA_DIR, 'icons'))) {
    if (name.endsWith('.png')) files[`icons/${name}`] = resolve(PWA_DIR, 'icons', name);
  }
  return files;
}

/**
 * PWA(12단계) — 새 의존성 없이(vite-plugin-pwa 대신) 이 플러그인 하나로:
 *  - 매니페스트·아이콘을 dist 루트(/manifest.json, /icons/*)에 낸다(개발 서버에서도 같은 주소로 낸다).
 *  - 서비스워커(src/sw/serviceWorker.ts)를 따로 묶어 /sw.js 로 낸다 — 앱 청크를 import 하지 않는 단일 파일.
 *  - sw.js 에 빌드 번호(산출물 이름 목록의 해시)와 미리 받을 정적 자산 목록을 끼워 넣는다. 자산 이름에 내용 해시가 붙어 있으므로
 *    무엇이든 바뀌면 빌드 번호가 바뀌고 → sw.js 바이트가 바뀌어 → 브라우저가 새 서비스워커를 설치한다(캐시 무효화).
 *    관리자 화면 청크는 미리 받지 않는다(운영자만 쓰는 코드).
 */
function territoryServiceWorker(): Plugin {
  let building = false;
  return {
    name: 'territory-service-worker',
    configResolved(config) {
      building = config.command === 'build';
    },
    configureServer(server) {
      const files = pwaFiles();
      server.middlewares.use((request, response, next) => {
        const path = (request.url ?? '').split('?')[0]?.replace(/^\//, '') ?? '';
        const source = files[path];
        if (!source) return next();
        response.setHeader('Content-Type', path.endsWith('.png') ? 'image/png' : 'application/manifest+json');
        response.end(readFileSync(source));
      });
    },
    buildStart() {
      if (!building) return;
      for (const [fileName, source] of Object.entries(pwaFiles())) this.emitFile({ type: 'asset', fileName, source: readFileSync(source) });
      this.emitFile({ type: 'chunk', id: SW_ENTRY, fileName: 'sw.js' });
    },
    generateBundle(_options, bundle) {
      const worker = bundle['sw.js'];
      if (!worker || worker.type !== 'chunk') return;
      if (worker.imports.length || worker.dynamicImports.length) this.error('sw.js 가 다른 청크를 import 한다 — src/sw/ 밖의 코드를 쓰지 않는다');
      const precache = Object.keys(bundle)
        .filter(name => name !== 'sw.js' && name !== 'index.html' && !name.endsWith('.map'))
        .filter(name => !/(^|\/)AdminApp-[^/]*$/.test(name))
        .sort()
        .map(name => '/' + name);
      const build = createHash('sha256').update(precache.join('\n')).digest('hex').slice(0, 12);
      worker.code = worker.code
        .replaceAll('__TERRITORY_SW_BUILD__', JSON.stringify(build))
        .replaceAll('__TERRITORY_SW_PRECACHE__', JSON.stringify(precache));
    },
  };
}

/**
 * 개발 서버(npm run dev)가 Spring 서버로 넘길 API 경로 — 이 목록 한 곳만 고친다.
 * 정적 화면(/, /assets/*)만 Vite 가 내고 나머지는 전부 서버 몫이다(세션 쿠키·CSRF 쿠키도 그대로 오간다).
 */
export const API_PATHS = [
  'explorers', 'territory', 'visits', 'maps', 'me', 'progress', 'collection', 'quests',
  'inventory', 'scene', 'friends', 'feed', 'rankings', 'compare', 'catalog', 'mystery', 'seasons', 'revisits', 'wishlist',
  'events', 'push', 'auth', 'logout', 'login', 'oauth2', 'u', 'dev', 'admin', 'health', 'actuator',
] as const;

/**
 * 프록시 대상 Spring 서버. 기본은 http://localhost:18081(bootRun --args='--server.port=18081') — bootRun 기본 포트 8080 은
 * 이 PC 에서 다른 앱이 쓰고 18080 은 운영 compose 자리라 피한다. 다른 포트면 VITE_API_TARGET=http://localhost:18082 npm run dev
 */
export const DEFAULT_API_TARGET = 'http://localhost:18081';
const API_TARGET = process.env.VITE_API_TARGET || DEFAULT_API_TARGET;

export default defineConfig({
  plugins: [react(), territoryServiceWorker()],
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
