package com.kobi.territory.analytics.application;

import com.kobi.territory.analytics.domain.metrics.CohortSnapshot;
import com.kobi.territory.analytics.domain.metrics.DailySnapshot;
import com.kobi.territory.analytics.domain.metrics.DayRange;
import com.kobi.territory.analytics.domain.metrics.MetricsReport;
import com.kobi.territory.analytics.domain.metrics.MetricsStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영 지표 조회({@code GET /admin/metrics}). 지난 날은 일 배치가 저장한 값, 오늘은 실시간 계산(같은 계산). 오늘 값은
 * territory.analytics.live-cache-ttl 동안 다시 계산하지 않는다(운영 MySQL 부하 — 30일 구간 집계가 있어서).
 */
@Service
public class MetricsQueryService {

    private final MetricsComputation computation;
    private final MetricsStore store;
    private final AnalyticsSettings settings;
    private final TransactionTemplate readTx;
    private final Clock clock;
    private final AtomicReference<LiveToday> live = new AtomicReference<>();

    public MetricsQueryService(MetricsComputation computation, MetricsStore store, AnalyticsSettings settings,
                               PlatformTransactionManager transactionManager, Clock clock) {
        this.computation = computation;
        this.store = store;
        this.settings = settings;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.clock = clock;
    }

    public MetricsReport report(int days) {
        Instant now = clock.instant();
        LocalDate today = settings.ingestPolicy().dayOf(now);
        DayRange range = settings.metricsPolicy().reportRange(today, days);
        LiveToday current = live.updateAndGet(cached -> cached != null && cached.freshFor(today, now, settings) ? cached
            : readTx.execute(status -> new LiveToday(today, computation.daily(today, today, now), computation.cohort(today, today, now), now)));
        return readTx.execute(status -> MetricsReport.assemble(range, store.daily(range), current.daily(), store.cohorts(range),
            current.cohort(), settings.metricsPolicy().backfillRange(today).from(), store.lastComputedAt().orElse(null), now));
    }

    /** 오늘 실시간 값(짧게 재사용). */
    private record LiveToday(LocalDate day, DailySnapshot daily, CohortSnapshot cohort, Instant computedAt) {

        boolean freshFor(LocalDate today, Instant now, AnalyticsSettings settings) {
            return day.equals(today) && computedAt.plus(settings.liveCacheTtl()).isAfter(now);
        }
    }
}
