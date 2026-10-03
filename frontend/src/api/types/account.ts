export type LoginOutcome = 'CREATED' | 'LINKED' | 'MERGED' | 'SIGNED_IN';

/** LoginResponse.MergeNotice */
export interface MergeSummary {
  fromExplorerId: string;
  movedRegions: number;
  newRegions: number;
}

/** 세션의 병합·연결 안내(LoginResponse) — 한 번 보여 주면 서버가 지운다 */
export interface MergeNotice {
  explorerId: string;
  handle: string;
  email: string | null;
  personalMapId: string;
  outcome: LoginOutcome;
  merge: MergeSummary | null;
}

/** GET /auth/session (AuthController.session) */
export interface SessionResponse {
  googleLoginEnabled: boolean;
  loginUrl: string | null;
  loggedIn: boolean;
  explorerId: string | null;
  handle: string | null;
  email: string | null;
  personalMapId: string | null;
  mergeNotice: MergeNotice | null;
}

/** POST /auth/login-intent */
export interface LoginIntentResponse {
  googleLoginEnabled: boolean;
  loginUrl: string | null;
}
