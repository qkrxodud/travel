package com.kobi.territory.notification.domain.push;

/** 기기 하나에 보낸 결과(푸시 서비스 응답). */
public enum SendOutcome {
    /** 받음(2xx). */
    DELIVERED,
    /** 구독이 없어졌다(404·410) — 그 기기를 지운다. */
    GONE,
    /** 잠시 뒤 다시(429·5xx·연결 실패·시간 초과). */
    RETRY,
    /** 이 요청은 받을 수 없다(400·401·403·413 — 키 불일치 등). 다시 보내도 같다. */
    REJECTED
}
