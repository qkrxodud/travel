/** 토스트(화면 상태). 3.2초 뒤 사라진다(프로토타입과 같게). */
import { create } from 'zustand';
import type { ApiError } from '../api/client';
import { errorToastCode, track } from '../api/analytics';
import type { ServerErrorCode } from '../api/types/common';

export interface Toast {
  id: number;
  icon: string;
  title: string;
  sub: string;
}

const TOAST_MS = 3200;
let toastSequence = 0;

interface ToastState {
  toasts: Toast[];
  push: (icon: string, title: string, sub?: string) => void;
  remove: (id: number) => void;
}

export const useToastStore = create<ToastState>()((set, get) => ({
  toasts: [],
  push: (icon, title, sub = '') => {
    toastSequence += 1;
    const id = toastSequence;
    set({ toasts: [...get().toasts, { id, icon, title, sub }] });
    setTimeout(() => get().remove(id), TOAST_MS);
  },
  remove: id => set({ toasts: get().toasts.filter(toast => toast.id !== id) }),
}));

/** 컴포넌트 밖(뮤테이션 콜백)에서 쓰는 토스트 */
export const toast = (icon: string, title: string, sub?: string): void => useToastStore.getState().push(icon, title, sub);

/** 서버 에러 코드 → 토스트 제목(프로토타입 ERR_TITLE 그대로) */
const ERROR_TITLE: Readonly<Partial<Record<ServerErrorCode, string>>> = {
  DAILY_CAP_EXCEEDED: '오늘은 여기까지', FUTURE_VISIT_DATE: '날짜를 확인해 주세요', MEMO_TOO_LONG: '메모가 길어요', DUPLICATE_VISIT: '이미 칠한 곳',
  QUEST_ALREADY_CLAIMED: '이미 받은 보상', QUEST_NOT_COMPLETED: '아직 달성 전', TITLE_NOT_EARNED: '아직 못 얻은 칭호',
  PHOTO_REQUIRED: '사진이 필요해요', NOT_A_MEMBER: '멤버가 아니에요', OWNER_ONLY: '지도장만 할 수 있어요', OWNER_CANNOT_LEAVE: '지도장은 탈퇴 전 양도',
  INVITE_CODE_NOT_FOUND: '초대코드 확인', MAP_FULL: '지도가 가득 찼어요', ALREADY_MEMBER: '이미 멤버예요',
  PROFILE_MAP_NOT_FOUND: '합류할 수 없는 지도', PROFILE_NOT_FOUND: '프로필 없음',
  ALREADY_FOLLOWING: '이미 팔로우 중', CANNOT_FOLLOW_SELF: '나는 팔로우할 수 없어요', CANNOT_COMPARE_SELF: '나와는 비교할 수 없어요',
  INVALID_VISIBILITY: '공개 범위 확인', CARD_KIND_NOT_FOUND: '모르는 카드',
  REVISIT_NOT_PAINTED: '아직 칠하지 않은 곳', REVISIT_SAME_YEAR: '내년부터 받을 수 있어요', REVISIT_ALREADY_STAMPED: '올해 도장은 받았어요',
  WISH_ALREADY_VISITED: '이미 다녀온 곳', WISHLIST_FULL: '가고 싶은 곳이 가득해요',
  HANDLE_INVALID: 'handle 형식 확인', HANDLE_RESERVED: '쓸 수 없는 handle', HANDLE_TAKEN: '이미 쓰는 handle', LOGIN_REQUIRED: '로그인이 필요해요', CSRF_INVALID: '새로고침이 필요해요',
};

export function errorTitle(code: string | undefined): string {
  return (code && ERROR_TITLE[code as ServerErrorCode]) || '요청 실패';
}

/** 실패 토스트(! 아이콘 · 코드별 제목 · 서버 메시지) */
export function toastError(error: unknown, message?: string): void {
  const apiError = error as Partial<ApiError> | null;
  const text = message ?? (error instanceof Error ? error.message : String(error));
  toast('!', errorTitle(apiError?.code), text);
  // 분석: 어떤 오류가 화면에 보였는지(코드만 — 메시지 문장은 싣지 않는다)
  track('error_toast', { code: errorToastCode(error) });
}
