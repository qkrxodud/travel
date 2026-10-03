/** 계정(4단계): 로그인 세션·구글 로그인 시작·로그아웃 */
import { apiClient } from './client';
import type { LoginIntentResponse, SessionResponse } from './types/account';

export const accountApi = {
  session: (): Promise<SessionResponse | null> => apiClient.session(),
  loginIntent: () => apiClient.loginIntent<LoginIntentResponse>(),
  logout: () => apiClient.logout(),
};
