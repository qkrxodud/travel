import { QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { createAdminQueryClient } from '../queries';
import { AdminPage } from './AdminPage';

/** 관리자 화면 뿌리(/#/admin) — 게임 화면과 캐시를 나누고, 탐험가 발급·분석 수집을 하지 않는다. */
export function AdminApp() {
  const [queryClient] = useState(createAdminQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <AdminPage />
    </QueryClientProvider>
  );
}
