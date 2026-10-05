/** 백엔드 common.model.Rarity */
export type Rarity = 'COMMON' | 'RARE' | 'LEGEND';

/** 백엔드 ErrorResponse(code, message) — code 는 분기용 안정 문자열. */
export interface ErrorResponse {
  code: string;
  message: string;
}

/**
 * 서버가 내는 에러 코드 전부 — 도메인 오류 enum(ExplorationError·ProgressionError·WardrobeError·SharingError·SocialError·CatalogError·
 * AccountError·AnalyticsError)의 상수 이름 + 보안·웹 계층 코드(ExplorerAuthentication·SecurityConfig·AdminTokenInterceptor·RegionCode·GlobalExceptionHandler).
 * 화면이 분기·제목에 쓰는 값이다. 모르는 코드는 HTTP_{status} 로 바꾼다(api/client.ts toErrorCode).
 * 서버에 코드를 더하거나 빼면 여기도 맞춘다(06 QA: 백엔드 grep 으로 전수 대조).
 */
export const SERVER_ERROR_CODES = [
  // 탐험(exploration)·공유 지도
  'DAILY_CAP_EXCEEDED', 'FUTURE_VISIT_DATE', 'INVALID_VISIT_DATE', 'MEMO_TOO_LONG', 'DUPLICATE_VISIT', 'VISIT_NOT_FOUND',
  'PHOTO_REQUIRED', 'INVALID_PHOTO_REF', 'NOT_A_MEMBER', 'OWNER_ONLY', 'OWNER_CANNOT_LEAVE', 'INVITE_CODE_NOT_FOUND',
  'MAP_FULL', 'MAP_NOT_FOUND', 'ALREADY_MEMBER', 'INVALID_MAP', 'INVALID_SETTINGS', 'PERSONAL_MAP_ONLY_ME',
  'REGION_NOT_FOUND', 'REGION_RETIRED', 'INVALID_REGION_CODE', 'EXPLORER_NOT_FOUND',
  // 9단계 재방문 도장·가고 싶은 곳
  'REVISIT_NOT_PAINTED', 'REVISIT_SAME_YEAR', 'REVISIT_ALREADY_STAMPED', 'WISH_ALREADY_VISITED', 'WISHLIST_FULL',
  // 계정·인증
  'EXPLORER_TOKEN_REQUIRED', 'EXPLORER_TOKEN_INVALID', 'ACCOUNT_NOT_FOUND', 'ACCOUNT_INVALID', 'LOGIN_REQUIRED',
  'HANDLE_INVALID', 'HANDLE_RESERVED', 'HANDLE_TAKEN', 'MERGE_NOT_ALLOWED', 'CSRF_INVALID',
  // 진행(progression)
  'QUEST_ALREADY_CLAIMED', 'QUEST_NOT_COMPLETED', 'QUEST_NOT_FOUND', 'QUEST_BOARD_CLOSED', 'TITLE_NOT_EARNED', 'TITLE_NOT_FOUND',
  // 꾸미기(wardrobe)
  'ITEM_NOT_FOUND', 'ITEM_NOT_OWNED', 'SLOT_MISMATCH', 'TOO_MANY_PROPS', 'DUPLICATE_PROP', 'UNKNOWN_ITEM_REFERENCE',
  // 공유(sharing)·소셜(social)
  'PROFILE_MAP_NOT_FOUND', 'PROFILE_NOT_FOUND', 'INVALID_VISIBILITY', 'CARD_KIND_NOT_FOUND', 'INVALID_YEAR',
  'ALREADY_FOLLOWING', 'CANNOT_FOLLOW_SELF', 'CANNOT_COMPARE_SELF',
  // 분석(analytics, 10단계) — 수집 오류는 화면에 보이지 않는다. INVALID_METRICS_RANGE 는 관리자 지표
  'INVALID_VISITOR_ID', 'EVENT_BATCH_TOO_LARGE', 'EVENTS_RATE_LIMITED', 'INVALID_METRICS_RANGE',
  // 알림(notification, 12단계 웹 푸시) — UNKNOWN_NOTIFICATION_KIND 는 local 즉시 발송(/dev/push/send)
  'INVALID_PUSH_SUBSCRIPTION', 'PUSH_ENDPOINT_NOT_ALLOWED', 'INVALID_PUSH_PREFERENCES', 'UNKNOWN_NOTIFICATION_KIND',
  // 관리자 API(화면은 쓰지 않지만 서버가 내는 코드)
  'ADMIN_TOKEN_REQUIRED', 'ADMIN_TOKEN_INVALID', 'INVALID_ITEM_DEFINITION', 'ITEM_ALREADY_EXISTS',
  // 13s단계 계절 회차 지역 목록(관리자 /admin/seasons/**)
  'SEASON_ROUND_NOT_FOUND', 'SEASON_ROUND_LOCKED', 'SEASON_CANDIDATE_MISSING', 'SEASON_LINEUP_BUSY',
  // 웹 계층(GlobalExceptionHandler)
  'VALIDATION_FAILED', 'MALFORMED_REQUEST', 'BAD_PARAMETER', 'NOT_FOUND', 'METHOD_NOT_ALLOWED', 'UNSUPPORTED_MEDIA_TYPE',
  'CONFLICT', 'CONCURRENT_UPDATE', 'INTERNAL_ERROR',
] as const;

export type ServerErrorCode = (typeof SERVER_ERROR_CODES)[number];

/** 화면이 만드는 코드: 발급 실패, 모르는 서버 코드·본문 없는 오류(HTTP_{status}). */
export type ErrorCode = ServerErrorCode | 'ISSUE_FAILED' | `HTTP_${number}`;
