package com.kobi.territory.notification.domain;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;

/** 알림 컨텍스트 오류 코드. code 문자열은 API 계약이다(화면 SERVER_ERROR_CODES 와 대조). */
public enum NotificationError {
    /** 브라우저 구독 정보(주소·p256dh·auth)의 형식이 틀림. */
    INVALID_PUSH_SUBSCRIPTION(ErrorKind.INVALID, "알림 구독 정보가 올바르지 않아요(%s)."),
    /** 알려진 푸시 서비스(브라우저 회사)가 아닌 주소 — 서버가 아무 곳에나 요청을 보내지 않게 한다. */
    PUSH_ENDPOINT_NOT_ALLOWED(ErrorKind.INVALID, "이 브라우저의 알림 서비스 주소는 받을 수 없어요."),
    /** 알림 설정의 종류별 켜고 끄기 값이 빠짐. */
    INVALID_PUSH_PREFERENCES(ErrorKind.INVALID, "알림 설정은 mystery·streak·season 을 모두 true/false 로 보내 주세요."),
    /** 모르는 알림 종류(local 즉시 발송). */
    UNKNOWN_NOTIFICATION_KIND(ErrorKind.INVALID, "알림 종류는 mystery·streak·season 중 하나예요.");

    private final ErrorKind kind;
    private final String template;

    NotificationError(ErrorKind kind, String template) {
        this.kind = kind;
        this.template = template;
    }

    public TerritoryException exception(Object... args) {
        return new TerritoryException(name(), kind, String.format(template, args));
    }
}
