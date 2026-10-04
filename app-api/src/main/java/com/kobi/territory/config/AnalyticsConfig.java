package com.kobi.territory.config;

import com.kobi.territory.analytics.application.AnalyticsSettings;
import com.kobi.territory.analytics.domain.journey.JourneyPolicy;
import com.kobi.territory.analytics.domain.metrics.MetricsPolicy;
import com.kobi.territory.analytics.domain.ratelimit.RateLimitPolicy;
import com.kobi.territory.analytics.domain.ratelimit.TrustedProxies;
import com.kobi.territory.analytics.domain.tracking.IngestPolicy;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 분석(10단계) 설정값 → 정책 값. 서버 비밀값 territory.analytics.salt 는 local 이 application-local.yml 고정값, 운영은 환경변수
 * TERRITORY_ANALYTICS_SALT 필수(application-prod.yml — 없으면 기동 실패). 일 배치 주기 territory.analytics.batch-cron 은 배치 빈이 직접 받는다.
 */
@Configuration
public class AnalyticsConfig {

    @Bean
    public AnalyticsSettings analyticsSettings(
        @Value("${territory.analytics.salt}") String salt,
        @Value("${territory.time-zone:Asia/Seoul}") String zone,
        @Value("${territory.analytics.ingest.max-batch-events:50}") int maxBatchEvents,
        @Value("${territory.analytics.ingest.max-body-bytes:32768}") int maxBodyBytes,
        @Value("${territory.analytics.ingest.max-clock-skew:24h}") Duration maxClockSkew,
        @Value("${territory.analytics.ingest.rate-limit.visitor-burst:20}") int visitorBurst,
        @Value("${territory.analytics.ingest.rate-limit.visitor-per-minute:30}") int visitorPerMinute,
        @Value("${territory.analytics.ingest.rate-limit.address-burst:60}") int addressBurst,
        @Value("${territory.analytics.ingest.rate-limit.address-per-minute:120}") int addressPerMinute,
        @Value("${territory.analytics.ingest.rate-limit.max-tracked-keys:100000}") int maxTrackedKeys,
        @Value("${territory.analytics.retention-days:90}") int retentionDays,
        @Value("${territory.analytics.recompute-days:35}") int recomputeDays,
        @Value("${territory.analytics.daily-recompute-days:3}") int dailyRecomputeDays,
        @Value("${territory.analytics.feature-window-days:7}") int featureWindowDays,
        @Value("${territory.analytics.k-window-days:30}") int kWindowDays,
        @Value("${territory.analytics.funnel.check-in-window-days:7}") int checkInWindowDays,
        @Value("${territory.analytics.funnel.revisit-window-days:7}") int revisitWindowDays,
        @Value("${territory.analytics.invite-attribution-days:7}") int inviteAttributionDays,
        @Value("${territory.analytics.max-report-days:90}") int maxReportDays,
        @Value("${territory.analytics.top-error-codes:10}") int topErrorCodes,
        @Value("${territory.analytics.live-cache-ttl:60s}") Duration liveCacheTtl,
        @Value("${territory.analytics.trusted-proxies:}") List<String> trustedProxies) {
        return new AnalyticsSettings(salt,
            new IngestPolicy(maxBatchEvents, maxClockSkew, ZoneId.of(zone)),
            new RateLimitPolicy(visitorBurst, visitorPerMinute, addressBurst, addressPerMinute, maxTrackedKeys),
            new JourneyPolicy(revisitWindowDays, inviteAttributionDays),
            new MetricsPolicy(retentionDays, recomputeDays, dailyRecomputeDays, featureWindowDays, kWindowDays, checkInWindowDays,
                revisitWindowDays, maxReportDays, topErrorCodes),
            maxBodyBytes, liveCacheTtl, TrustedProxies.of(trustedProxies));
    }
}
