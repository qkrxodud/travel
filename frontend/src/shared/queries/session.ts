/** 로그인 세션 — 앱 셸·프로필·랭킹이 함께 읽는다. */
import { useQuery } from '@tanstack/react-query';
import { accountApi } from '../../api/account';

export const sessionKeys = {
  session: () => ['auth', 'session'] as const,
};

/** 앱 수명 동안 한 번 읽는다(병합 안내는 한 번만 온다). 로그아웃하면 다시 읽는다. */
export function useSession() {
  return useQuery({ queryKey: sessionKeys.session(), queryFn: accountApi.session, staleTime: Infinity });
}
