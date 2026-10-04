package com.kobi.territory.analytics.domain.ratelimit;

import com.kobi.territory.analytics.domain.AnalyticsError;
import java.time.Instant;

/**
 * 화면 이벤트 수집 문지기 — 방문 ID 버킷과 주소 버킷을 둘 다 통과해야 받는다. 인스턴스 메모리에만 있다(단일 인스턴스 가정 —
 * {@link TokenBuckets}). 주소는 이 판단에만 쓰고 저장하지 않는다.
 */
public final class IngestThrottle {

    private final TokenBuckets visitors;
    private final TokenBuckets addresses;

    public IngestThrottle(RateLimitPolicy policy) {
        this.visitors = new TokenBuckets(policy.visitorBurst(), policy.visitorPerMinute(), policy.maxTrackedKeys());
        this.addresses = new TokenBuckets(policy.addressBurst(), policy.addressPerMinute(), policy.maxTrackedKeys());
    }

    /** 받을 수 있으면 그대로, 너무 잦으면 거절(EVENTS_RATE_LIMITED). 주소를 모르면 방문 ID 만 본다. */
    public void admit(String visitorKey, String addressKey, Instant now) {
        boolean addressAllowed = addressKey == null || addresses.tryTake(addressKey, now);
        if (!addressAllowed || !visitors.tryTake(visitorKey, now)) throw AnalyticsError.EVENTS_RATE_LIMITED.exception();
    }
}
