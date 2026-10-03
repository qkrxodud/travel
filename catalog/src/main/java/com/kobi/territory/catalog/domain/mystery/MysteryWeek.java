package com.kobi.territory.catalog.domain.mystery;

import com.kobi.territory.common.model.RegionCode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * 한 주의 미스터리 지역(mystery_week 행 — 선택 기록). 주는 월요일 00:00(서비스 시간대, Asia/Seoul)에 시작해 다음 월요일 00:00 직전까지.
 * 전체 사용자 공통이고 한 번 기록되면 바뀌지 않는다(서버를 다시 띄워도, 방문자 통계가 바뀌어도 같은 주는 같은 지역).
 *
 * @param weekStart  그 주 월요일(주 id — 장부 refId 의 {week})
 * @param selectedAt 고른 처리 시각
 */
public record MysteryWeek(LocalDate weekStart, RegionCode region, Instant selectedAt) {

    public MysteryWeek {
        Objects.requireNonNull(weekStart, "weekStart");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(selectedAt, "selectedAt");
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) throw new IllegalArgumentException("주는 월요일에 시작한다: " + weekStart);
    }

    /** 처리 시각 at 이 속한 주의 월요일(zone 기준). */
    public static LocalDate weekStartOf(Instant at, ZoneId zone) {
        return LocalDate.ofInstant(at, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * 기록이 없는 주를 지금(now) 고를 수 있는지 — 지금 주만. 지난 주를 나중에 고르지 않는다(소급 없음: 그 주 체크인은 보너스가 없다),
     * 다음 주를 미리 고르지도 않는다(그 주의 통계로 고른다).
     */
    public static boolean drawableAt(LocalDate weekStart, Instant now, ZoneId zone) {
        return weekStartOf(now, zone).equals(weekStart);
    }

    /** 지금(now) 기준 이미 지난 주인지 — 지난 주는 기록이 없으면 앞으로도 없다(다시 고르지 않는다). */
    public static boolean pastAt(LocalDate weekStart, Instant now, ZoneId zone) {
        return weekStart.isBefore(weekStartOf(now, zone));
    }

    /** 이 주가 시작하는 순간(월요일 00:00). */
    public Instant startsAt(ZoneId zone) {
        return weekStart.atStartOfDay(zone).toInstant();
    }

    /** 이 주가 끝나는 순간(다음 월요일 00:00 — 이 순간부터는 다음 주). */
    public Instant endsAt(ZoneId zone) {
        return weekStart.plusWeeks(1).atStartOfDay(zone).toInstant();
    }

    /** 처리 시각 at 이 이 주 안인지. */
    public boolean covers(Instant at, ZoneId zone) {
        return weekStartOf(at, zone).equals(weekStart);
    }
}
