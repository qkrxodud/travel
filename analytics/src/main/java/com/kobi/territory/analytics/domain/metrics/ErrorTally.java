package com.kobi.territory.analytics.domain.metrics;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 구간 안에 화면 오류 토스트로 뜬 오류 코드별 횟수. */
public final class ErrorTally {

    private final DayRange range;
    private final Map<String, Integer> countsByCode;

    private ErrorTally(DayRange range, Map<String, Integer> countsByCode) {
        this.range = Objects.requireNonNull(range, "range");
        this.countsByCode = Map.copyOf(countsByCode);
    }

    public static ErrorTally of(DayRange range, Map<String, Integer> countsByCode) {
        return new ErrorTally(range, countsByCode);
    }

    public DayRange range() {
        return range;
    }

    /** 많이 뜬 코드 먼저 limit 개(같으면 코드 이름 순). */
    public List<ErrorCount> top(int limit) {
        return countsByCode.entrySet().stream()
            .map(entry -> new ErrorCount(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingInt(ErrorCount::count).reversed().thenComparing(ErrorCount::code))
            .limit(limit)
            .toList();
    }

    public int total() {
        return countsByCode.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ErrorTally tally && range.equals(tally.range) && countsByCode.equals(tally.countsByCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, countsByCode);
    }
}
