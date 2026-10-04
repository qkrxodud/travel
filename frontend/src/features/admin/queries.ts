/**
 * 운영 지표 쿼리. 관리자 토큰은 부르는 화면의 메모리(useState)에만 있고 쿼리 키에는 넣지 않는다 —
 * 대신 "몇 번째로 입력한 토큰인지"(session)를 키에 넣어, 토큰을 다시 넣으면 새로 읽는다.
 */
import { QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../../api/admin';

export const adminKeys = {
  all: () => ['admin'] as const,
  metrics: (session: number, days: number) => ['admin', 'metrics', session, days] as const,
};

/** 관리자 화면 전용 캐시 — 실패는 화면이 직접 보여 준다(게임 화면의 오류 토스트·재시도 없음) */
export function createAdminQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, refetchOnWindowFocus: false, refetchOnReconnect: false, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}

export function useMetrics(adminToken: string | null, session: number, days: number) {
  return useQuery({
    queryKey: adminKeys.metrics(session, days),
    queryFn: () => adminApi.metrics(adminToken ?? '', days),
    enabled: adminToken !== null,
  });
}

/** 일 배치 실행 → 지표 다시 읽기 */
export function useRunBatch(adminToken: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => adminApi.runBatch(adminToken ?? ''),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.all() }),
  });
}
