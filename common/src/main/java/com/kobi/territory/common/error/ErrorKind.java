package com.kobi.territory.common.error;

/** 오류 분류. HTTP 상태 매핑은 app-api 전역 예외 처리가 담당한다(도메인은 HTTP를 모른다). */
public enum ErrorKind {
    /** 입력 형식·길이 위반 → 400 */
    INVALID,
    /** 탐험가 식별 불가 → 401 */
    UNAUTHENTICATED,
    /** 권한 없음(지도 멤버 아님 등) → 403 */
    FORBIDDEN,
    /** 대상 없음 → 404 */
    NOT_FOUND,
    /** 중복·상태 충돌 → 409 */
    CONFLICT,
    /** 게임 규칙 위반(미래 날짜, 하루 상한 등) → 422 */
    RULE_VIOLATION
}
