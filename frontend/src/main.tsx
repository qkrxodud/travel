import { QueryClientProvider } from '@tanstack/react-query';
import { lazy, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import { analytics } from './api/analytics';
import { App } from './app/App';
import { createQueryClient } from './app/queryClient';
import { sprites } from './shared/lib/pixel';
import './styles/global.css';

/** 관리자 지표 화면(/#/admin) — 게임과 같은 페이지지만 따로 그린다(코드도 따로 받는다). 탐험가 발급·분석 수집을 하지 않는다. */
const ADMIN_HASH = '#/admin';
const isAdminRoute = () => location.hash.startsWith(ADMIN_HASH);
const AdminApp = lazy(() => import('./features/admin/components/AdminApp').then(module => ({ default: module.AdminApp })));

const adminRoute = isAdminRoute();
// 주소창에서 게임 ↔ 관리자 화면을 오가면 새로 연다(두 화면은 상태를 나누지 않는다)
window.addEventListener('hashchange', () => {
  if (isAdminRoute() !== adminRoute) location.reload();
});

const root = document.getElementById('root');
if (root && adminRoute) {
  document.title = '나의 영토 · 운영 지표';
  createRoot(root).render(
    <Suspense fallback={null}>
      <AdminApp />
    </Suspense>,
  );
} else if (root) {
  sprites.preload();
  analytics.start();
  const queryClient = createQueryClient();
  createRoot(root).render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  );
}
