package com.kobi.territory.notification.domain.delivery;

import com.kobi.territory.notification.domain.policy.QuietHours;
import java.time.Duration;
import java.util.Objects;

/**
 * 발송 규칙(설정 territory.push.*).
 *
 * @param quietHours   조용한 시간(기본 22~08시, Asia/Seoul) — 이 시간에는 보내지 않는다
 * @param dailyLimit   한 사람이 하루(서울 날짜)에 받는 알림 최대 개수(기본 1)
 * @param retry        재시도
 * @param claimTimeout 보내는 중(SENDING)으로 이 시간 넘게 머문 기록은 발송기가 죽은 것으로 보고 다시 잡는다
 * @param timeToLive   기기가 꺼져 있을 때 푸시 서비스가 들고 있을 시간(TTL 헤더)
 */
public record DeliveryPolicy(QuietHours quietHours, int dailyLimit, RetryPolicy retry, Duration claimTimeout, Duration timeToLive) {

    public DeliveryPolicy {
        Objects.requireNonNull(quietHours, "quietHours");
        Objects.requireNonNull(retry, "retry");
        Objects.requireNonNull(claimTimeout, "claimTimeout");
        Objects.requireNonNull(timeToLive, "timeToLive");
        if (dailyLimit < 1) throw new IllegalArgumentException("daily-limit 는 1 이상");
        if (claimTimeout.isNegative() || claimTimeout.isZero()) throw new IllegalArgumentException("claim-timeout 은 양수");
        if (timeToLive.isNegative() || timeToLive.toDays() > 28) throw new IllegalArgumentException("ttl 은 0 ~ 28일");
    }
}
