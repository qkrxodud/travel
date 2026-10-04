package com.kobi.territory.analytics.domain;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;

/** 분석 컨텍스트 오류 코드. code 문자열은 API 계약이다(화면은 수집 오류를 사용자에게 보이지 않는다 — 버리고 넘어간다). */
public enum AnalyticsError {
    /** 묶음 모양이 틀림(이벤트 자리에 null 등) — 전역 JSON 오류와 같은 코드. */
    MALFORMED_REQUEST(ErrorKind.INVALID, "요청 본문을 읽을 수 없습니다(이벤트 묶음 형식 확인)."),
    /** 익명 방문 ID 가 없거나 형식이 틀림(영문·숫자·-·_ 8~64자). */
    INVALID_VISITOR_ID(ErrorKind.INVALID, "방문 ID 형식이 올바르지 않아요."),
    /** 한 번에 보낸 이벤트 수·본문 크기가 상한을 넘음. */
    EVENT_BATCH_TOO_LARGE(ErrorKind.PAYLOAD_TOO_LARGE, "한 번에 보낼 수 있는 이벤트는 %s 까지예요."),
    /** 같은 방문(또는 같은 주소)에서 짧은 시간에 너무 많이 보냄. */
    EVENTS_RATE_LIMITED(ErrorKind.TOO_MANY_REQUESTS, "이벤트를 너무 자주 보냈어요. 잠시 뒤 다시 보내 주세요."),
    /** 지표 조회 기간이 허용 범위 밖. */
    INVALID_METRICS_RANGE(ErrorKind.INVALID, "지표 기간은 1~%d일이에요.");

    private final ErrorKind kind;
    private final String template;

    AnalyticsError(ErrorKind kind, String template) {
        this.kind = kind;
        this.template = template;
    }

    public TerritoryException exception(Object... args) {
        return new TerritoryException(name(), kind, String.format(template, args));
    }
}
