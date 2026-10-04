package com.kobi.territory.analytics.application;

import com.kobi.territory.analytics.domain.actor.VisitorRepository;
import com.kobi.territory.analytics.domain.journey.ExplorerJourneyRepository;
import com.kobi.territory.analytics.domain.metrics.DayRange;
import com.kobi.territory.analytics.domain.metrics.MetricsPolicy;
import com.kobi.territory.analytics.domain.metrics.MetricsStore;
import com.kobi.territory.analytics.domain.tracking.TrackedEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 분석 일 배치(territory.analytics.batch-cron, 기본 매일 04:10 서울, "-" 면 끔). 원본이 남아 있는 지난 날(보관 기간 안) 중 — 하루 지표는
 * 최근 daily-recompute-days 일과 아직 계산하지 않은 날, 코호트(퍼널·리텐션)는 최근 recompute-days 일과 아직 계산하지 않은 날을 계산해 통째로
 * 바꾸고(늦게 온 사실·D30 리텐션이 채워지도록, 지표 조회의 빈 날이 실제로 채워지도록), 보관 기간이 지난 원본 이벤트와, 마지막 활동이 보관
 * 기간보다 오래된 방문·여정을 지운다(집계는 남는다). 하루씩 짧은 트랜잭션으로 —
 * 운영 MySQL 을 오래 잡지 않는다. 같은 입력이면 언제 다시 돌려도 같은 결과다. 수동 실행: 운영 {@code POST /admin/metrics/batch},
 * local {@code POST /dev/analytics/batch}.
 */
@Component
public class MetricsBatchJob {

    private static final Logger log = LoggerFactory.getLogger(MetricsBatchJob.class);

    private final MetricsComputation computation;
    private final MetricsStore store;
    private final TrackedEventRepository events;
    private final VisitorRepository visitors;
    private final ExplorerJourneyRepository journeys;
    private final MetricsPolicy policy;
    private final AnalyticsSettings settings;
    private final TransactionTemplate dayTx;
    private final Clock clock;

    public MetricsBatchJob(MetricsComputation computation, MetricsStore store, TrackedEventRepository events, VisitorRepository visitors,
                           ExplorerJourneyRepository journeys, AnalyticsSettings settings, PlatformTransactionManager transactionManager,
                           Clock clock) {
        this.visitors = visitors;
        this.journeys = journeys;
        this.computation = computation;
        this.store = store;
        this.events = events;
        this.policy = settings.metricsPolicy();
        this.settings = settings;
        this.dayTx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(cron = "${territory.analytics.batch-cron:0 10 4 * * *}", zone = "${territory.time-zone:Asia/Seoul}")
    public BatchResult run() {
        long started = System.nanoTime();
        Instant now = clock.instant();
        LocalDate today = settings.ingestPolicy().dayOf(now);
        DayRange backfill = policy.backfillRange(today);
        List<LocalDate> dailyDays = policy.dailyDaysToRecompute(today, store.computedDays(backfill));
        List<LocalDate> cohortDays = policy.cohortDaysToRecompute(today, store.computedCohortDays(backfill));
        dailyDays.forEach(day -> dayTx.executeWithoutResult(status -> store.replaceDaily(computation.daily(day, today, now))));
        cohortDays.forEach(day -> dayTx.executeWithoutResult(status -> store.replaceCohort(computation.cohort(day, today, now))));
        int purged = events.purgeBefore(policy.purgeBefore(today));
        int forgottenVisitors = visitors.purgeInactive(policy.purgeBefore(today));
        int forgottenJourneys = journeys.purgeInactive(policy.purgeBefore(today));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        log.info("분석 일 배치: 하루 지표 {}일·코호트 {}일(원본이 남은 {} ~ {} 중) 계산, 보관 기간 지난 원본 {}줄·방문 {}·여정 {} 삭제, {}ms",
            dailyDays.size(), cohortDays.size(), backfill.from(), backfill.to(), purged, forgottenVisitors, forgottenJourneys, elapsedMs);
        return new BatchResult(backfill.from(), backfill.to(), dailyDays.size(), cohortDays.size(), purged, forgottenVisitors,
            forgottenJourneys, elapsedMs);
    }

    /** 배치 한 번의 결과. */
    /**
     * @param from        계산할 수 있는 구간(원본이 남은 날) 시작 — to 는 어제
     * @param dailyDays   하루 지표를 계산한 날 수(최근 며칠 + 비어 있던 날)
     * @param cohortDays  코호트를 다시 계산한 날 수
     * @param purged           지운 원본 이벤트 줄 수
     * @param purgedVisitors   마지막 활동이 보관 기간보다 오래돼 지운 방문 수
     * @param purgedJourneys   마지막 활동이 보관 기간보다 오래돼 지운 탐험가 여정 수
     */
    public record BatchResult(LocalDate from, LocalDate to, int dailyDays, int cohortDays, int purged, int purgedVisitors,
                              int purgedJourneys, long elapsedMs) {}
}
