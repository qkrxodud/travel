/**
 * 관리자 화면 쿼리. 관리자 토큰은 부르는 화면의 메모리(useState)에만 있고 쿼리 키에는 넣지 않는다 —
 * 대신 "몇 번째로 입력한 토큰인지"(session)를 키에 넣어, 토큰을 다시 넣으면 새로 읽는다.
 */
import { QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../../api/admin';
import type { RoundLineupResponse } from '../../api/types/catalog';

export const adminKeys = {
  all: () => ['admin'] as const,
  metrics: (session: number, days: number) => ['admin', 'metrics', session, days] as const,
  seasonLineups: (session: number) => ['admin', 'season-lineups', session] as const,
  seasonLineup: (session: number, roundId: string) => ['admin', 'season-lineup', session, roundId] as const,
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

export function useMetrics(adminToken: string | null, session: number, days: number, enabled = true) {
  return useQuery({
    queryKey: adminKeys.metrics(session, days),
    queryFn: () => adminApi.metrics(adminToken ?? '', days),
    enabled: enabled && adminToken !== null,
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

/** 계절 회차 목록 + TourAPI 사용량(13s단계) */
export function useSeasonLineups(adminToken: string | null, session: number, enabled = true) {
  return useQuery({
    queryKey: adminKeys.seasonLineups(session),
    queryFn: () => adminApi.seasonLineups(adminToken ?? ''),
    enabled: enabled && adminToken !== null,
  });
}

/** 회차 하나(지난 회차도 — 그때 쓴 목록) */
export function useSeasonLineup(adminToken: string | null, session: number, roundId: string | null) {
  return useQuery({
    queryKey: adminKeys.seasonLineup(session, roundId ?? ''),
    queryFn: () => adminApi.seasonLineup(adminToken ?? '', roundId ?? ''),
    enabled: adminToken !== null && roundId !== null,
  });
}

/** 갱신·확정의 공통 반영: 응답(회차 하나)을 상세 캐시에 넣고, 목록·사용량을 다시 읽는다 */
function useLineupMutation<Variables>(session: number, request: (variables: Variables) => Promise<RoundLineupResponse>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (round: RoundLineupResponse) => {
      queryClient.setQueryData(adminKeys.seasonLineup(session, round.roundId), round);
      return queryClient.invalidateQueries({ queryKey: adminKeys.seasonLineups(session) });
    },
  });
}

/** 지금 모아 후보로(확정 아님) — fresh 면 같은 날 캐시를 건너뛴다 */
export function useRefreshSeasonLineup(adminToken: string | null, session: number) {
  return useLineupMutation(session, ({ roundId, fresh }: { roundId: string; fresh: boolean }) =>
    adminApi.refreshSeasonLineup(adminToken ?? '', roundId, fresh));
}

/** 후보를 확정 */
export function useConfirmSeasonLineup(adminToken: string | null, session: number) {
  return useLineupMutation(session, ({ roundId }: { roundId: string }) => adminApi.confirmSeasonLineup(adminToken ?? '', roundId));
}
