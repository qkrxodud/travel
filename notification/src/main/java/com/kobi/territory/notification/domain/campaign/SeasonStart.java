package com.kobi.territory.notification.domain.campaign;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Objects;

/**
 * 계절 한정 테마 정의 중 알림에 필요한 것(카탈로그 seasons.json). 회차 id = {@code {id}-{시작 연도}}(진행의 SeasonRound 와 같은 규칙).
 */
public record SeasonStart(String id, String name, String emoji, MonthDay start, MonthDay end) {

    public SeasonStart {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        emoji = emoji == null ? "" : emoji;
    }

    /** 그 날짜가 이 계절의 시작일인지. */
    public boolean startsOn(LocalDate day) {
        return MonthDay.from(day).equals(start);
    }

    /** 그 날짜에 열려 있는 회차의 시작 연도(연말을 넘는 계절이면 앞 해), 열려 있지 않으면 -1. */
    int openYear(LocalDate day) {
        int year = day.getYear();
        if (!start.isAfter(end)) return !MonthDay.from(day).isBefore(start) && !MonthDay.from(day).isAfter(end) ? year : -1;
        if (!MonthDay.from(day).isBefore(start)) return year;
        return !MonthDay.from(day).isAfter(end) ? year - 1 : -1;
    }

    public String roundId(int year) {
        return id + "-" + year;
    }
}
