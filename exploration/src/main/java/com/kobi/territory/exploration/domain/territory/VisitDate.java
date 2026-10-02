package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.exploration.domain.ExplorationError;
import java.time.LocalDate;

/** 방문일(기록용 표시 값). 과거 허용, 미래 거부는 Territory가 오늘 날짜와 비교해 판단한다. */
public record VisitDate(LocalDate value) {
    public VisitDate {
        if (value == null) throw ExplorationError.INVALID_VISIT_DATE.exception();
    }

    public static VisitDate of(LocalDate value) {
        return new VisitDate(value);
    }

    public boolean isAfter(LocalDate today) {
        return value.isAfter(today);
    }
}
