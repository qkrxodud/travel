package com.kobi.territory.analytics.domain.metrics;

import java.time.LocalDate;
import java.util.function.Function;

/**
 * D1/D7/D30 리텐션(코호트 = 그날 가입한 탐험가). DN = 가입한 날로부터 N일째 되는 날 하루에 활동한 탐험가 수(그날 하루 — 그 사이가 아니다).
 * 아직 그날이 다 지나지 않았으면 셀 수 없어 null.
 */
public record RetentionCounts(int newExplorers, Integer day1, Integer day7, Integer day30) {

    /** 셀 수 있는 날만 retainedOn(활동한 날)으로 센다. */
    public static RetentionCounts measure(LocalDate cohortDay, LocalDate today, int newExplorers, MetricsPolicy policy,
                                          Function<LocalDate, Integer> retainedOn) {
        Integer[] counts = MetricsPolicy.RETENTION_OFFSETS.stream()
            .map(offset -> policy.observable(cohortDay, offset, today) ? retainedOn.apply(cohortDay.plusDays(offset)) : null)
            .toArray(Integer[]::new);
        return new RetentionCounts(newExplorers, counts[0], counts[1], counts[2]);
    }

    public Double day1Rate() {
        return day1 == null ? null : Ratio.of(day1, newExplorers);
    }

    public Double day7Rate() {
        return day7 == null ? null : Ratio.of(day7, newExplorers);
    }

    public Double day30Rate() {
        return day30 == null ? null : Ratio.of(day30, newExplorers);
    }
}
