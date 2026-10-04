package com.kobi.territory.analytics.api.web;

import com.kobi.territory.analytics.application.MetricsBatchJob;
import com.kobi.territory.analytics.domain.AnalyticsError;
import com.kobi.territory.analytics.domain.metrics.CohortSnapshot;
import com.kobi.territory.analytics.domain.metrics.DailySnapshot;
import com.kobi.territory.analytics.domain.metrics.ErrorCount;
import com.kobi.territory.analytics.domain.metrics.FeatureUse;
import com.kobi.territory.analytics.domain.metrics.KFactor;
import com.kobi.territory.analytics.domain.metrics.MetricsReport;
import com.kobi.territory.analytics.domain.tracking.BatchOutcome;
import com.kobi.territory.analytics.domain.tracking.Rejection;
import com.kobi.territory.analytics.domain.tracking.SubmittedEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 분석 웹 DTO(계약 {@code _workspace/10_contracts.md}). */
public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    // ---- POST /events ----

    /**
     * @param visitorId 익명 방문 ID(브라우저 localStorage 의 랜덤 값)
     * @param events    이벤트 묶음(상한 territory.analytics.ingest.max-batch-events)
     */
    public record EventsRequest(String visitorId, List<EventItem> events) {
        /** 이벤트 자리에 null 이 있으면 묶음 모양이 틀린 것(400 MALFORMED_REQUEST — 서버 오류가 아니다). */
        List<SubmittedEvent> submitted() {
            if (events == null) return List.of();
            if (events.stream().anyMatch(Objects::isNull)) throw AnalyticsError.MALFORMED_REQUEST.exception();
            return events.stream().map(EventItem::toSubmitted).toList();
        }
    }

    /**
     * @param name  이벤트 이름(허용 목록)
     * @param at    화면이 잰 발생 시각(ISO-8601, 선택 — 형식이 틀리거나 너무 옛날·미래면 서버가 받은 시각)
     * @param props 필드(정의된 것만)
     */
    public record EventItem(String name, String at, Map<String, Object> props) {
        SubmittedEvent toSubmitted() {
            return new SubmittedEvent(name, parse(at), props);
        }

        private static Instant parse(String at) {
            if (at == null) return null;
            try {
                return Instant.parse(at);
            } catch (DateTimeParseException invalid) {
                return null;
            }
        }
    }

    /** 202 응답: 적은 수와 받지 않은 이벤트(몇 번째·이름·이유). 봇 요청은 accepted 0 · rejected 빈 목록(오류 아님). */
    public record EventsResponse(int accepted, List<RejectedEvent> rejected) {
        static EventsResponse from(BatchOutcome outcome) {
            return new EventsResponse(outcome.accepted().size(), outcome.rejected().stream().map(RejectedEvent::from).toList());
        }
    }

    public record RejectedEvent(int index, String name, String reason) {
        static RejectedEvent from(Rejection rejection) {
            return new RejectedEvent(rejection.index(), rejection.name(), rejection.reason().name());
        }
    }

    // ---- GET /admin/metrics ----

    public record MetricsResponse(Instant generatedAt, String timeZone, LocalDate from, LocalDate to, Instant lastBatchAt,
                                  List<LocalDate> missingDays, List<LocalDate> expiredDays, TodayView today, List<DailyView> daily, List<FunnelView> funnel,
                                  List<RetentionView> retention, KFactorView kFactor, FeatureUsageView featureUsage,
                                  TopErrorsView topErrors) {

        static MetricsResponse from(MetricsReport report, String timeZone, int topErrorCodes) {
            DailySnapshot today = report.today();
            return new MetricsResponse(report.generatedAt(), timeZone, report.range().from(), report.range().to(), report.lastBatchAt(),
                report.missingDays(), report.expiredDays(), TodayView.from(today),
                report.daily().stream().map(snapshot -> DailyView.from(snapshot, today.day())).toList(),
                report.cohorts().stream().map(FunnelView::from).toList(),
                report.cohorts().stream().map(RetentionView::from).toList(),
                KFactorView.from(today.kFactor()), FeatureUsageView.from(today), TopErrorsView.from(today, topErrorCodes));
        }
    }

    /** 오늘(실시간) 머리 숫자. */
    public record TodayView(LocalDate day, int newVisitors, int newExplorers, int dau, int wau, int mau, Instant computedAt) {
        static TodayView from(DailySnapshot snapshot) {
            return new TodayView(snapshot.day(), snapshot.newVisitors(), snapshot.newExplorers(), snapshot.dau(), snapshot.wau(),
                snapshot.mau(), snapshot.computedAt());
        }
    }

    public record DailyView(LocalDate day, int newVisitors, int newExplorers, int dau, int wau, int mau, int profileViews,
                            int cardViews, int botViews, Double kFactor, boolean live) {
        static DailyView from(DailySnapshot snapshot, LocalDate today) {
            return new DailyView(snapshot.day(), snapshot.newVisitors(), snapshot.newExplorers(), snapshot.dau(), snapshot.wau(),
                snapshot.mau(), snapshot.pageViews().profileViews(), snapshot.pageViews().cardViews(), snapshot.pageViews().botViews(),
                snapshot.kFactor().value(), snapshot.day().equals(today));
        }
    }

    public record FunnelView(LocalDate cohortDay, int firstScreen, int firstCheckIn, int revisitedWithin7Days, Double checkInRate,
                             Double revisitRate, Double overallRate, boolean settled) {
        static FunnelView from(CohortSnapshot cohort) {
            var funnel = cohort.funnel();
            return new FunnelView(cohort.cohortDay(), funnel.firstScreen(), funnel.firstCheckIn(), funnel.revisited(),
                funnel.checkInRate(), funnel.revisitRate(), funnel.overallRate(), funnel.settled());
        }
    }

    public record RetentionView(LocalDate cohortDay, int newExplorers, Integer d1, Integer d7, Integer d30, Double d1Rate,
                                Double d7Rate, Double d30Rate) {
        static RetentionView from(CohortSnapshot cohort) {
            var retention = cohort.retention();
            return new RetentionView(cohort.cohortDay(), retention.newExplorers(), retention.day1(), retention.day7(),
                retention.day30(), retention.day1Rate(), retention.day7Rate(), retention.day30Rate());
        }
    }

    public record KFactorView(LocalDate from, LocalDate to, int invitedNewExplorers, int cardNewExplorers, int viralNewExplorers,
                              int activeExplorers, Double value) {
        static KFactorView from(KFactor kFactor) {
            return new KFactorView(kFactor.range().from(), kFactor.range().to(), kFactor.invitedNewExplorers(),
                kFactor.cardNewExplorers(), kFactor.viralNewExplorers(), kFactor.activeExplorers(), kFactor.value());
        }
    }

    public record FeatureUsageView(LocalDate from, LocalDate to, int activeUsers, List<FeatureUse> features) {
        static FeatureUsageView from(DailySnapshot today) {
            var usage = today.featureUsage();
            return new FeatureUsageView(usage.range().from(), usage.range().to(), usage.activeUsers(), usage.ranked());
        }
    }

    public record TopErrorsView(LocalDate from, LocalDate to, int total, List<ErrorCount> codes) {
        static TopErrorsView from(DailySnapshot today, int limit) {
            var errors = today.errors();
            return new TopErrorsView(errors.range().from(), errors.range().to(), errors.total(), errors.top(limit));
        }
    }

    /** {@code POST /admin/metrics/batch} 응답. */
    public record BatchResponse(LocalDate from, LocalDate to, int dailyDays, int cohortDays, int purged, int purgedVisitors,
                                int purgedJourneys, long elapsedMs) {
        static BatchResponse from(MetricsBatchJob.BatchResult result) {
            return new BatchResponse(result.from(), result.to(), result.dailyDays(), result.cohortDays(), result.purged(),
                result.purgedVisitors(), result.purgedJourneys(), result.elapsedMs());
        }
    }
}
