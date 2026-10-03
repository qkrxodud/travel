/** 단위 테스트 공용: 화면이 들고 있는 서버 값(TanStack Query 캐시)과 그 캐시로 감싼 훅 렌더러 */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/** 이미 읽어 둔 서버 값(키 → 값)으로 시작하는 화면. stale(key) = 그 값을 다시 읽어야 하는 상태인지. */
export function serverState(known: readonly (readonly [readonly unknown[], unknown])[] = []) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  for (const [key, value] of known) queryClient.setQueryData(key, value);
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  const stale = (key: readonly unknown[]) => queryClient.getQueryState(key)?.isInvalidated;
  return { queryClient, wrapper, stale };
}
