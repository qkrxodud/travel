package com.kobi.territory.analytics.domain.tracking;

/** 화면 이벤트 하나를 받지 않은 이유(응답 {@code rejected[].reason}). */
public enum RejectionReason {
    /** 허용 목록에 없는 이벤트 이름 */
    UNKNOWN_EVENT,
    /** 서버만 적는 이벤트 이름을 화면이 보냄(가입·체크인 등은 서버 사실로만 센다) */
    SERVER_ONLY_EVENT,
    /** 개인정보로 보이는 필드(메모·handle·이메일·IP·User-Agent·위치 등) — 이벤트째 버린다 */
    PERSONAL_DATA,
    /** 그 이벤트에 정의되지 않은 필드 */
    UNKNOWN_FIELD,
    /** 꼭 있어야 하는 필드가 없음 */
    MISSING_FIELD,
    /** 필드 값의 형식·목록이 틀림 */
    INVALID_FIELD
}
