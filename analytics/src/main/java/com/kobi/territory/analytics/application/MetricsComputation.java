package com.kobi.territory.analytics.application;

import com.kobi.territory.analytics.domain.metrics.CohortSnapshot;
import com.kobi.territory.analytics.domain.metrics.DailySnapshot;
import com.kobi.territory.analytics.domain.metrics.DayRange;
import com.kobi.territory.analytics.domain.metrics.ErrorTally;
import com.kobi.territory.analytics.domain.metrics.FeatureUsage;
import com.kobi.territory.analytics.domain.metrics.FunnelCounts;
import com.kobi.territory.analytics.domain.metrics.KFactor;
import com.kobi.territory.analytics.domain.metrics.MetricsPolicy;
import com.kobi.territory.analytics.domain.metrics.MetricsReader;
import com.kobi.territory.analytics.domain.metrics.RetentionCounts;
import com.kobi.territory.analytics.domain.tracking.EventDefinitions;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 하루 지표·코호트 하나를 센다 — 구간은 지표 정의(MetricsPolicy)가 정하고, 수는 집계 질의(MetricsReader)가 센다. 일 배치(지난 날)와
 * 지표 조회(오늘)가 같은 계산을 쓴다. 호출자가 연 읽기 트랜잭션 안에서 부른다.
 */
@Component
class MetricsComputation {

    private final MetricsReader reader;
    private final MetricsPolicy policy;
    private final List<String> features = EventDefinitions.standard().featureNames();

    MetricsComputation(MetricsReader reader, AnalyticsSettings settings) {
        this.reader = reader;
        this.policy = settings.metricsPolicy();
    }

    /** today = 계산하는 날(배치는 오늘, 실시간은 그날) — 구간 앞부분 원본이 지워졌는지 판단에 쓴다. */
    DailySnapshot daily(LocalDate day, LocalDate today, Instant computedAt) {
        DayRange featureRange = policy.featureRange(day);
        DayRange kRange = policy.kRange(day);
        KFactor kFactor = new KFactor(kRange, reader.invitedNewExplorers(kRange), reader.cardNewExplorers(kRange),
            reader.viralNewExplorers(kRange), reader.activeExplorers(kRange));
        return new DailySnapshot(day, reader.newVisitors(day), reader.newExplorers(day), reader.activeActors(DayRange.single(day)),
            reader.activeActors(policy.weekEnding(day)), reader.activeActors(policy.monthEnding(day)), reader.pageViews(day), kFactor,
            FeatureUsage.of(featureRange, reader.activeActors(featureRange), features, reader.featureUsers(featureRange, features)),
            ErrorTally.of(featureRange, reader.errorCounts(featureRange)), computedAt, policy.windowTruncated(day, today));
    }

    CohortSnapshot cohort(LocalDate cohortDay, LocalDate today, Instant computedAt) {
        DayRange checkInWindow = policy.checkInWindow(cohortDay);
        FunnelCounts funnel = new FunnelCounts(reader.funnelFirstScreen(cohortDay), reader.funnelFirstCheckIn(cohortDay, checkInWindow),
            reader.funnelRevisited(cohortDay, checkInWindow), policy.funnelSettled(cohortDay, today));
        RetentionCounts retention = RetentionCounts.measure(cohortDay, today, reader.cohortSize(cohortDay), policy,
            activeDay -> reader.retained(cohortDay, activeDay));
        return new CohortSnapshot(cohortDay, funnel, retention, computedAt);
    }
}
