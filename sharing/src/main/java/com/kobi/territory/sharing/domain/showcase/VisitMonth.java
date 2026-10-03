package com.kobi.territory.sharing.domain.showcase;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 공개용 방문 시기 — 날짜를 월 단위로 둥글린 값(§7 프라이버시: 공개 프로필·카드의 날짜는 월 단위). 일(日)은 밖으로 나가지 않는다.
 */
public record VisitMonth(YearMonth month) {
    public VisitMonth {
        Objects.requireNonNull(month, "month");
    }

    public static VisitMonth of(LocalDate visitDate) {
        return new VisitMonth(YearMonth.from(Objects.requireNonNull(visitDate, "visitDate")));
    }

    /** "2026년 10월" */
    public String label() {
        return month.getYear() + "년 " + month.getMonthValue() + "월";
    }

    /** "2026-10" (기계 판독용) */
    public String iso() {
        return month.toString();
    }
}
