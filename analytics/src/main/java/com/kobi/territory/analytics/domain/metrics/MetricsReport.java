package com.kobi.territory.analytics.domain.metrics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 운영자가 보는 지표 묶음 — 지난 날은 일 배치가 저장한 값, 오늘은 방금 계산한 값.
 *
 * @param range       조회 구간(오늘로 끝남)
 * @param today       오늘 지표(실시간)
 * @param daily       구간 안 하루 지표(오래된 날 먼저, 오늘 포함)
 * @param cohorts     구간 안 코호트(오래된 날 먼저, 오늘 포함)
 * @param missingDays 구간 안인데 아직 배치가 계산하지 않은 지난 날 — 원본이 남아 있어 배치를 돌리면 채워진다
 * @param expiredDays 구간 안인데 계산한 적이 없고 원본 보관 기간도 지나 다시 셀 수 없는 날(빈 날로 세지 않는다)
 * @param lastBatchAt 마지막 일 배치 시각(없으면 null)
 */
public record MetricsReport(DayRange range, DailySnapshot today, List<DailySnapshot> daily, List<CohortSnapshot> cohorts,
                            List<LocalDate> missingDays, List<LocalDate> expiredDays, Instant lastBatchAt, Instant generatedAt) {

    public MetricsReport {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(today, "today");
        daily = List.copyOf(daily);
        cohorts = List.copyOf(cohorts);
        missingDays = List.copyOf(missingDays);
        expiredDays = List.copyOf(expiredDays);
    }

    /**
     * 저장된 지난 날 + 오늘(실시간)을 날짜 순으로 합친다. 저장 값에 오늘 줄이 있어도 실시간 값을 쓴다. 계산 안 된 지난 날은 원본이 남은 날
     * (backfillFrom 이후 — 배치가 채운다)과 보관 기간이 지난 날로 나눈다.
     */
    public static MetricsReport assemble(DayRange range, List<DailySnapshot> storedDaily, DailySnapshot today,
                                         List<CohortSnapshot> storedCohorts, CohortSnapshot todayCohort, LocalDate backfillFrom,
                                         Instant lastBatchAt, Instant generatedAt) {
        List<DailySnapshot> daily = merge(storedDaily, today, DailySnapshot::day);
        List<CohortSnapshot> cohorts = merge(storedCohorts, todayCohort, CohortSnapshot::cohortDay);
        Set<LocalDate> computed = daily.stream().map(DailySnapshot::day).collect(Collectors.toSet());
        List<LocalDate> notComputed = range.from().datesUntil(today.day()).filter(day -> !computed.contains(day)).toList();
        List<LocalDate> missing = notComputed.stream().filter(day -> !day.isBefore(backfillFrom)).toList();
        List<LocalDate> expired = notComputed.stream().filter(day -> day.isBefore(backfillFrom)).toList();
        return new MetricsReport(range, today, daily, cohorts, missing, expired, lastBatchAt, generatedAt);
    }

    private static <T> List<T> merge(List<T> stored, T live, Function<T, LocalDate> dayOf) {
        LocalDate liveDay = dayOf.apply(live);
        List<T> merged = new ArrayList<>(stored.stream().filter(item -> !dayOf.apply(item).equals(liveDay)).toList());
        merged.add(live);
        merged.sort(Comparator.comparing(dayOf));
        return merged;
    }
}
