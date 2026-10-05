package com.kobi.territory.config;

import com.kobi.territory.catalog.application.SeasonLineupSettings;
import com.kobi.territory.catalog.application.TourApiSettings;
import com.kobi.territory.catalog.domain.lineup.CollectionSchedule;
import com.kobi.territory.catalog.domain.lineup.LineupPolicy;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 계절 명소 TourAPI 연동(13s단계) 설정값 → 정책 값. 서비스 키(TOURAPI_SERVICE_KEY)는 선택 값이다 — 없으면 아무 호출 없이 기본 목록(AI 추정)을 쓰고,
 * 넣고 재기동하면 다음 회차 후보를 자동으로 모은다(doc/operations.md 13s단계). 값은 로그에 남기지 않는다.
 */
@Configuration
public class SeasonLineupConfig {

    @Bean
    public TourApiSettings tourApiSettings(
        @Value("${territory.tourapi.service-key:}") String serviceKey,
        @Value("${territory.tourapi.base-url:https://apis.data.go.kr/B551011/KorService2}") String baseUrl,
        @Value("${territory.tourapi.mobile-app:Territory}") String mobileApp,
        @Value("${territory.tourapi.timeout:10s}") Duration timeout,
        @Value("${territory.tourapi.retries:2}") int retries,
        @Value("${territory.tourapi.retry-backoff:1s}") Duration retryBackoff,
        @Value("${territory.tourapi.page-size:1000}") int pageSize,
        @Value("${territory.tourapi.max-pages:3}") int maxPages,
        @Value("${territory.tourapi.daily-call-limit:200}") int dailyCallLimit) {
        return new TourApiSettings(serviceKey, baseUrl, mobileApp, timeout, retries, retryBackoff, pageSize, maxPages, dailyCallLimit);
    }

    @Bean
    public SeasonLineupSettings seasonLineupSettings(
        @Value("${territory.tourapi.lineup.size:10}") int size,
        @Value("${territory.tourapi.lineup.margin-days:14}") int marginDays,
        @Value("${territory.tourapi.lineup.evidence-per-region:5}") int evidencePerRegion,
        @Value("${territory.tourapi.lineup.boundary-tolerance-km:3}") double boundaryToleranceKilometers,
        @Value("${territory.tourapi.collect.lead-days:30}") int leadDays,
        @Value("${territory.tourapi.collect.recollect-after:7d}") Duration recollectAfter,
        @Value("${territory.tourapi.collect.auto-confirm:true}") boolean autoConfirm,
        @Value("${territory.tourapi.collect.auto-confirm-min-regions:10}") int autoConfirmMinRegions,
        @Value("${territory.tourapi.collect.on-startup:true}") boolean collectOnStartup,
        @Value("${territory.time-zone:Asia/Seoul}") String zone) {
        return new SeasonLineupSettings(new LineupPolicy(size, marginDays, evidencePerRegion),
            new CollectionSchedule(leadDays, recollectAfter, autoConfirm, autoConfirmMinRegions, ZoneId.of(zone)), boundaryToleranceKilometers,
            collectOnStartup);
    }
}
