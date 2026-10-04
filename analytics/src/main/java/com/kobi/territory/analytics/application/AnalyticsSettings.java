package com.kobi.territory.analytics.application;

import com.kobi.territory.analytics.domain.journey.JourneyPolicy;
import com.kobi.territory.analytics.domain.metrics.MetricsPolicy;
import com.kobi.territory.analytics.domain.ratelimit.RateLimitPolicy;
import com.kobi.territory.analytics.domain.ratelimit.TrustedProxies;
import com.kobi.territory.analytics.domain.tracking.IngestPolicy;
import java.time.Duration;
import java.util.Objects;

/**
 * 분석 설정값(app-api 가 territory.analytics.* 를 바인딩해 만든다).
 *
 * @param salt         탐험가 해시에 섞는 서버 비밀값(local 고정값, 운영은 TERRITORY_ANALYTICS_SALT 필수)
 * @param maxBodyBytes {@code POST /events} 본문 크기 상한
 * @param liveCacheTtl 오늘(실시간) 지표를 다시 계산하지 않고 재사용하는 시간(운영 부하 — local 은 0)
 * @param trustedProxies 믿는 프록시(CIDR·주소, territory.analytics.trusted-proxies — 기본 없음). 이 주소에서 온 요청만 CF-Connecting-IP·
 *                       X-Forwarded-For·CF-IPCountry 를 믿는다
 */
public record AnalyticsSettings(String salt, IngestPolicy ingestPolicy, RateLimitPolicy rateLimitPolicy, JourneyPolicy journeyPolicy,
                                MetricsPolicy metricsPolicy, int maxBodyBytes, Duration liveCacheTtl, TrustedProxies trustedProxies) {

    public AnalyticsSettings {
        if (salt == null || salt.isBlank()) throw new IllegalArgumentException("territory.analytics.salt 가 비어 있다");
        Objects.requireNonNull(ingestPolicy, "ingestPolicy");
        Objects.requireNonNull(rateLimitPolicy, "rateLimitPolicy");
        Objects.requireNonNull(journeyPolicy, "journeyPolicy");
        Objects.requireNonNull(metricsPolicy, "metricsPolicy");
        Objects.requireNonNull(liveCacheTtl, "liveCacheTtl");
        Objects.requireNonNull(trustedProxies, "trustedProxies");
        if (maxBodyBytes < 1024) throw new IllegalArgumentException("max-body-bytes 는 1024 이상");
        if (journeyPolicy.revisitWindowDays() != metricsPolicy.revisitWindowDays()) {
            throw new IllegalArgumentException("재방문 구간은 여정과 지표가 같아야 한다");
        }
    }

    @Override
    public String toString() {
        return "AnalyticsSettings[salt=****, " + ingestPolicy + ", " + rateLimitPolicy + ", " + journeyPolicy + ", " + metricsPolicy
            + ", maxBodyBytes=" + maxBodyBytes + ", liveCacheTtl=" + liveCacheTtl + ", " + trustedProxies + "]";
    }
}
