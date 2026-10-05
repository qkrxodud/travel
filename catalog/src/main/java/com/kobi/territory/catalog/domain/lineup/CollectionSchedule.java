package com.kobi.territory.catalog.domain.lineup;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 자동 수집 정책(설정값 territory.tourapi.* · territory.time-zone). 날짜 판단(하루 한 번·다시 모으는 간격)은 서비스 시간대의 <b>달력 날짜</b>로
 * 한다 — 24시간 롤링이면 매일 같은 시각에 도는 스케줄이 조금 일찍 끝난 날 하루씩 밀린다.
 *
 * @param leadDays              회차 시작 며칠 전부터 자동 수집·확정하는지
 * @param recollectAfter        그 기간 안에서 다시 모으는 간격(하루 이상, 날짜 단위 — 자동 수집은 회차당 하루 한 번 이하, 일일 호출 예산 보호)
 * @param autoConfirm           자동 수집 결과를 자동 확정하는지(관리자가 확정한 회차는 덮지 않는다)
 * @param autoConfirmMinRegions 자동 확정에 필요한 TourAPI 근거 지역 수 — 모자라면 후보로만 두고 지금 목록을 유지한다(관리자는 확정할 수 있다)
 * @param zone                  "하루"의 기준 시간대
 */
public record CollectionSchedule(int leadDays, Duration recollectAfter, boolean autoConfirm, int autoConfirmMinRegions, ZoneId zone) {

    public CollectionSchedule {
        if (leadDays < 0) throw new IllegalArgumentException("수집 시작 일수는 0 이상");
        Objects.requireNonNull(recollectAfter, "recollectAfter");
        if (recollectAfter.toDays() < 1) throw new IllegalArgumentException("다시 모으는 간격은 하루 이상");
        if (autoConfirmMinRegions < 1) throw new IllegalArgumentException("자동 확정 최소 근거 지역 수는 1 이상");
        Objects.requireNonNull(zone, "zone");
    }

    /** 두 시각이 서비스 시간대로 같은 날인지(또는 앞의 것이 뒤의 날 이후인지). */
    public boolean sameDayOrLater(Instant earlier, Instant at) {
        return !dayOf(earlier).isBefore(dayOf(at));
    }

    /** 마지막으로 모은 날부터 다시 모으는 간격(날짜)이 지났는지. */
    public boolean recollectDue(Instant lastCollected, Instant at) {
        return !dayOf(lastCollected).plusDays(recollectAfter.toDays()).isAfter(dayOf(at));
    }

    private LocalDate dayOf(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }
}
