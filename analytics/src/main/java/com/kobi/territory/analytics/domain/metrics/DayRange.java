package com.kobi.territory.analytics.domain.metrics;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 양 끝을 포함하는 날짜 구간(서울 날짜). */
public record DayRange(LocalDate from, LocalDate to) {

    public DayRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) throw new IllegalArgumentException("구간 시작이 끝보다 늦다: " + from + " ~ " + to);
    }

    /** day 로 끝나는 days 일(day 포함). 예: 7일 = day−6 ~ day. */
    public static DayRange ending(LocalDate day, int days) {
        if (days < 1) throw new IllegalArgumentException("구간은 1일 이상");
        return new DayRange(day.minusDays(days - 1L), day);
    }

    public static DayRange single(LocalDate day) {
        return new DayRange(day, day);
    }

    public int days() {
        return (int) ChronoUnit.DAYS.between(from, to) + 1;
    }

    public boolean contains(LocalDate day) {
        return !day.isBefore(from) && !day.isAfter(to);
    }
}
