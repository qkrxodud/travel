package com.kobi.territory.catalog.api.query;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 한 주의 미스터리 지역(공개 Query DTO, 8단계). 주는 월요일 00:00(서비스 시간대)에 시작한다.
 *
 * @param weekStart  그 주 월요일 — 주 id(진행 장부 refId {@code mystery:{explorerId}:{weekStart}})
 * @param startsAt   주가 시작하는 순간
 * @param endsAt     주가 끝나는 순간(다음 월요일 00:00 — 이 순간부터 다음 주)
 * @param regionCode 이번 주 미스터리 지역(KR-xxxxx)
 */
public record MysteryWeekView(LocalDate weekStart, Instant startsAt, Instant endsAt, String regionCode, Instant selectedAt) {

    /** 장부·소식에 쓰는 주 id(weekStart ISO 날짜). */
    public String weekId() {
        return weekStart.toString();
    }
}
