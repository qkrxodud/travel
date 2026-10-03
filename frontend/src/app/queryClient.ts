/**
 * 서버 상태 캐시. 프로토타입처럼 화면이 포커스를 얻는다고 다시 읽지 않고(재조회는 변경 직후 반영 대기 창에서만),
 * 실패는 재시도 없이 바로 토스트로 알린다. meta.silent 인 쿼리는 실패를 화면이 직접 다룬다(상위 %·카드 미리보기 등).
 */
import { QueryCache, QueryClient } from '@tanstack/react-query';
import { toastError } from '../store/toastStore';

declare module '@tanstack/react-query' {
  interface Register {
    queryMeta: { silent?: boolean };
  }
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    queryCache: new QueryCache({
      onError: (error, query) => {
        if (!query.meta?.silent) toastError(error);
      },
    }),
    defaultOptions: {
      queries: { retry: false, refetchOnWindowFocus: false, refetchOnReconnect: false, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}
