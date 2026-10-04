package com.kobi.territory.analytics.domain.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 믿는 프록시 설정 경고(10단계 QA r2 P3-d) — 프록시 뒤인데 {@code TERRITORY_TRUSTED_PROXIES} 가 비었거나 맞지 않아 보이는 요청이 오면
 * 경고하되, 같은 경고로 로그를 채우지 않게 interval 에 한 번만(인스턴스 메모리). 클라이언트가 헤더를 위조해도 경고가 늘지 않는다.
 */
public final class ProxySetupWarning {

    private final TrustedProxies trustedProxies;
    private final Duration interval;
    private Instant lastWarnedAt;

    public ProxySetupWarning(TrustedProxies trustedProxies, Duration interval) {
        this.trustedProxies = Objects.requireNonNull(trustedProxies, "trustedProxies");
        this.interval = Objects.requireNonNull(interval, "interval");
    }

    /** 지금 경고할지 — 설정이 맞지 않아 보이는 요청이고 마지막 경고 뒤 interval 이 지났으면 true(그리고 지금을 기억한다). */
    public synchronized boolean shouldWarn(ClientOrigin origin, Instant now) {
        if (!trustedProxies.ignoresForwardingFrom(origin)) return false;
        if (lastWarnedAt != null && now.isBefore(lastWarnedAt.plus(interval))) return false;
        lastWarnedAt = now;
        return true;
    }
}
