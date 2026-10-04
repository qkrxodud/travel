package com.kobi.territory.analytics.domain.metrics;

import com.kobi.territory.analytics.domain.AnalyticsError;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 지표 정의의 기간들(설정 territory.analytics.*). 정의 자체는 계약 문서 §3 에 있다.
 *
 * @param retentionDays       원본 이벤트 보관 일수(기본 90) — 지난 원본은 배치가 지운다(집계는 남는다)
 * @param recomputeDays       일 배치가 코호트(퍼널·리텐션)를 늘 다시 계산하는 지난 날 수(기본 35) — D30 리텐션이 마저 채워지도록.
 *                            그 앞은 원본이 남아 있는(보관 기간 안) 날 중 아직 계산하지 않은 날만 채운다
 * @param dailyRecomputeDays  하루 지표를 늘 다시 계산하는 최근 날 수(기본 3) — 늦게 도착한 서버 사실(릴레이 지연)을 반영하도록
 * @param featureWindowDays   기능별 사용률·상위 오류 코드 구간(기본 7일)
 * @param kWindowDays         K 계수 구간(기본 30일)
 * @param checkInWindowDays   퍼널 2단계 — 첫 화면 날부터 이 일수 안의 첫 체크인(기본 7)
 * @param revisitWindowDays   퍼널 3단계 — 첫 체크인 다음 날부터 이 일수 안의 재방문(기본 7, 여정 기준과 같은 값)
 * @param maxReportDays       지표 조회 한 번에 볼 수 있는 일수(기본 90)
 * @param topErrorCodes       상위 오류 코드 개수(기본 10)
 */
public record MetricsPolicy(int retentionDays, int recomputeDays, int dailyRecomputeDays, int featureWindowDays, int kWindowDays,
                            int checkInWindowDays, int revisitWindowDays, int maxReportDays, int topErrorCodes) {

    /** 리텐션은 가입한 날로부터 1·7·30일째 날에 활동했는지(D1/D7/D30). */
    public static final List<Integer> RETENTION_OFFSETS = List.of(1, 7, 30);

    public MetricsPolicy {
        if (dailyRecomputeDays < 1 || dailyRecomputeDays > recomputeDays) {
            throw new IllegalArgumentException("daily-recompute-days 는 1 ~ recompute-days");
        }
        if (featureWindowDays < 1 || kWindowDays < 1 || checkInWindowDays < 0 || revisitWindowDays < 1 || maxReportDays < 1
            || topErrorCodes < 1) {
            throw new IllegalArgumentException("지표 기간 설정이 올바르지 않다");
        }
        int settle = Math.max(RETENTION_OFFSETS.get(RETENTION_OFFSETS.size() - 1), checkInWindowDays + revisitWindowDays) + 1;
        if (recomputeDays < settle) {
            throw new IllegalArgumentException("recompute-days(" + recomputeDays + ")는 코호트가 확정되는 " + settle + "일 이상이어야 한다");
        }
        if (retentionDays <= recomputeDays + kWindowDays || retentionDays <= recomputeDays + featureWindowDays) {
            throw new IllegalArgumentException("retention-days(" + retentionDays + ")는 다시 계산하는 날의 집계 구간보다 길어야 한다");
        }
    }

    /**
     * 일 배치가 코호트(퍼널·리텐션)를 계산할 날들(오래된 날 먼저): 최근 recomputeDays 일은 늘(D30 이 채워지도록), 그 앞은 원본이 남아 있는 날
     * 중 아직 계산하지 않은 날만(배치가 멈췄던 구간·처음 배포 — 지표 조회의 빈 날과 같은 범위).
     */
    public List<LocalDate> cohortDaysToRecompute(LocalDate today, Set<LocalDate> computed) {
        return pending(today, recomputeDays, computed);
    }

    /**
     * 일 배치가 하루 지표를 계산할 날들(오래된 날 먼저): 최근 dailyRecomputeDays 일은 늘(늦게 온 서버 사실), 그 앞은 원본이 남아 있는 날 중 아직
     * 계산하지 않은 날만. 하루 지표는 그날로 끝나는 30일 구간을 세므로 무겁다 — 이미 계산한 지난 날을 매일 다시 세지 않는다(운영 MySQL 부하).
     * 오래된 빈 날을 늦게 채우면 그날의 30일 구간 앞부분 원본이 이미 지워졌을 수 있다(MAU·K 계수가 작게 나온다 — 운영 문서).
     */
    public List<LocalDate> dailyDaysToRecompute(LocalDate today, Set<LocalDate> computed) {
        return pending(today, dailyRecomputeDays, computed);
    }

    /**
     * 그날 하루 지표의 30일 구간(MAU·K 계수 — 더 긴 쪽)의 앞부분 원본이 today 기준 이미 보관 기간을 지나 지워졌는지(10단계 QA r2 P3-c). 오래된
     * 빈 날을 늦게 채우면 참이고, 그 값은 작게 나온다 — 화면이 "신뢰도 낮음"으로 표시한다.
     */
    public boolean windowTruncated(LocalDate day, LocalDate today) {
        LocalDate windowStart = monthEnding(day).from().isBefore(kRange(day).from()) ? monthEnding(day).from() : kRange(day).from();
        return windowStart.isBefore(purgeBefore(today));
    }

    /** 원본이 남아 있어 계산할 수 있는 지난 날 구간: 보관 기간의 첫날 ~ 어제. */
    public DayRange backfillRange(LocalDate today) {
        return new DayRange(purgeBefore(today), today.minusDays(1));
    }

    private List<LocalDate> pending(LocalDate today, int alwaysDays, Set<LocalDate> computed) {
        LocalDate alwaysFrom = today.minusDays(alwaysDays);
        return backfillRange(today).from().datesUntil(today)
            .filter(day -> !day.isBefore(alwaysFrom) || !computed.contains(day)).toList();
    }

    /** 이 날짜 이전의 원본은 지운다(보관 기간 retentionDays 일). */
    public LocalDate purgeBefore(LocalDate today) {
        return today.minusDays(retentionDays);
    }

    public DayRange weekEnding(LocalDate day) {
        return DayRange.ending(day, 7);
    }

    public DayRange monthEnding(LocalDate day) {
        return DayRange.ending(day, 30);
    }

    public DayRange featureRange(LocalDate day) {
        return DayRange.ending(day, featureWindowDays);
    }

    public DayRange kRange(LocalDate day) {
        return DayRange.ending(day, kWindowDays);
    }

    /** 퍼널 2단계 구간: 첫 화면 날 ~ +checkInWindowDays. */
    public DayRange checkInWindow(LocalDate cohortDay) {
        return new DayRange(cohortDay, cohortDay.plusDays(checkInWindowDays));
    }

    /** 퍼널이 더는 바뀌지 않는지 — 첫 체크인 구간과 재방문 구간이 모두 지났다. */
    public boolean funnelSettled(LocalDate cohortDay, LocalDate today) {
        return today.isAfter(cohortDay.plusDays((long) checkInWindowDays + revisitWindowDays));
    }

    /** 코호트의 N일째 날이 다 지났는지(지나야 그날 활동을 셀 수 있다). */
    public boolean observable(LocalDate cohortDay, int offset, LocalDate today) {
        return today.isAfter(cohortDay.plusDays(offset));
    }

    /** 오늘로 끝나는 조회 구간. 1 ~ maxReportDays 일. */
    public DayRange reportRange(LocalDate today, int days) {
        if (days < 1 || days > maxReportDays) throw AnalyticsError.INVALID_METRICS_RANGE.exception(maxReportDays);
        return DayRange.ending(today, days);
    }
}
