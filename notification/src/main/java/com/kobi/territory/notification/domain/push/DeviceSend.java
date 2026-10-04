package com.kobi.territory.notification.domain.push;

import java.time.Duration;
import java.util.Objects;

/**
 * 기기 하나에 보낸 결과.
 *
 * @param retryAfter 푸시 서비스가 알려 준 다시 보낼 때까지의 시간(없으면 0)
 * @param detail     로그·발송 기록용 짧은 설명(HTTP 상태 등 — 주소·키는 넣지 않는다)
 */
public record DeviceSend(PushEndpoint endpoint, SendOutcome outcome, Duration retryAfter, String detail) {

    public DeviceSend {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(outcome, "outcome");
        retryAfter = retryAfter == null || retryAfter.isNegative() ? Duration.ZERO : retryAfter;
        detail = detail == null ? outcome.name() : detail;
    }
}
