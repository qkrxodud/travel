package com.kobi.territory.notification.domain.policy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 조용한 시간(기본 22:00 ~ 다음 날 08:00, 서비스 시간대 Asia/Seoul) — 이 시간에는 알림을 보내지 않고 다음 허용 시각으로 미룬다.
 * 시작이 끝보다 늦으면 자정을 넘는 구간, 같으면 조용한 시간이 없다. 시작은 포함, 끝은 제외(08:00 정각은 보낼 수 있다).
 */
public record QuietHours(LocalTime start, LocalTime end, ZoneId zone) {

    public QuietHours {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(zone, "zone");
    }

    /** 이 시각에 보내도 되는지. */
    public boolean allows(Instant at) {
        return !quiet(at.atZone(zone).toLocalTime());
    }

    /** 이 시각 또는 그 뒤의 가장 이른 허용 시각(조용한 시간이면 끝나는 시각, 아니면 그대로). */
    public Instant nextAllowed(Instant at) {
        ZonedDateTime local = at.atZone(zone);
        if (!quiet(local.toLocalTime())) return at;
        LocalDate endDay = !local.toLocalTime().isBefore(start) && start.isAfter(end) ? local.toLocalDate().plusDays(1)
            : local.toLocalDate();
        return endDay.atTime(end).atZone(zone).toInstant();
    }

    /** 서비스 시간대의 날짜(하루 최대 개수의 "하루"). */
    public LocalDate dayOf(Instant at) {
        return at.atZone(zone).toLocalDate();
    }

    private boolean quiet(LocalTime time) {
        if (start.equals(end)) return false;
        if (start.isBefore(end)) return !time.isBefore(start) && time.isBefore(end);
        return !time.isBefore(start) || time.isBefore(end);
    }
}
