package com.kobi.territory.progression.domain.quest;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;

/** 퀘스트 보드 기간: 월간 'yyyy-MM' 또는 상시 도전 센티넬 'ALL'. */
public record QuestPeriod(String value) {

    public static final QuestPeriod ALL = new QuestPeriod("ALL");

    public QuestPeriod {
        Objects.requireNonNull(value, "period");
        if (!value.equals("ALL")) YearMonth.parse(value);
    }

    public static QuestPeriod of(YearMonth month) {
        return new QuestPeriod(month.toString());
    }

    /** 처리 시각이 속한 달(시간대 기준). */
    public static QuestPeriod monthOf(Instant at, ZoneId zone) {
        return of(YearMonth.from(at.atZone(zone)));
    }

    public boolean always() {
        return this.equals(ALL);
    }

    /** 지난 달 보드인지(current 기준). 상시 보드는 닫히지 않는다. */
    public boolean closedAt(YearMonth current) {
        return !always() && YearMonth.parse(value).isBefore(current);
    }
}
