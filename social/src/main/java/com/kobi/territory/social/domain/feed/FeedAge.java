package com.kobi.territory.social.domain.feed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 소식의 상대 시각(피드는 정확한 시각을 내보내지 않는다 — §7 프라이버시, 일 단위): 오늘 · 어제 · N일 전(~6일) · N주 전(~4주) ·
 * N개월 전. 기준 날짜는 서버 시간대(territory.time-zone)의 달력 날짜.
 *
 * @param daysAgo 오늘 기준 며칠 전(미래면 0)
 */
public record FeedAge(int daysAgo, String label) {

    public static FeedAge of(Instant occurredAt, Instant now, ZoneId zone) {
        LocalDate day = occurredAt.atZone(zone).toLocalDate();
        LocalDate today = now.atZone(zone).toLocalDate();
        int days = (int) Math.max(0, ChronoUnit.DAYS.between(day, today));
        return new FeedAge(days, label(days));
    }

    private static String label(int days) {
        if (days == 0) return "오늘";
        if (days == 1) return "어제";
        if (days < 7) return days + "일 전";
        if (days < 30) return (days / 7) + "주 전";
        return Math.max(1, days / 30) + "개월 전";
    }
}
