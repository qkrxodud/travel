package com.kobi.territory.progression.domain.progress;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 탐험 스트릭 — 매달 새 지역 1곳 이상 체크인하면 연속이 이어진다. 처리 시각(visitedAt) 기준이라 소급이 없다.
 * 8단계 보호권: 빈 달이 생긴 뒤 다시 칠할 때 빈 달 수만큼 보호권이 있으면 그만큼 써서 연속을 잇는다(빈 달은 개월 수에 더하지 않는다).
 * 모자라면 끊기고 보호권은 쓰지 않는다.
 * 달을 넘나드는 연속 값이라 QuestBoard 가 아니라 ExplorerProgress 에 둔다.
 * 다음 상태를 계산하는 값 객체라 record 가 아니라 class(class vs record 기준).
 */
public final class Streak {

    public static final Streak NONE = new Streak(0, null);

    /** lastMonth 에서 끝나는 연속 개월 수. */
    private final int months;
    /** 마지막으로 체크인한 달(없으면 null). */
    private final YearMonth lastMonth;

    private Streak(int months, YearMonth lastMonth) {
        if (months < 0 || (months > 0) != (lastMonth != null)) throw new IllegalArgumentException("streak 상태 오류");
        this.months = months;
        this.lastMonth = lastMonth;
    }

    /** 저장된 값으로 복원(0개월이면 NONE). */
    public static Streak of(int months, YearMonth lastMonth) {
        return months == 0 && lastMonth == null ? NONE : new Streak(months, lastMonth);
    }

    /** month 에 체크인했을 때의 스트릭. 같은 달이면 그대로, 바로 다음 달이면 +1, 그 밖(끊김)이면 1부터. 과거 달은 무시. */
    public Streak record(YearMonth month) {
        if (lastMonth == null) return new Streak(1, month);
        if (!month.isAfter(lastMonth)) return this;
        return month.equals(lastMonth.plusMonths(1)) ? new Streak(months + 1, month) : new Streak(1, month);
    }

    /**
     * month 에 체크인했을 때의 스트릭과 쓸 보호권 수(8단계). 빈 달이 있으면 빈 달 수만큼 보호권(freezesHeld)이 있을 때만 이어 가고(+1),
     * 모자라면 1부터 다시(보호권은 쓰지 않는다). 같은 달·과거 달은 그대로.
     */
    public StreakStep record(YearMonth month, int freezesHeld) {
        if (lastMonth == null || !month.isAfter(lastMonth)) return new StreakStep(record(month), 0);
        int gap = emptyMonthsBefore(month);
        if (gap == 0) return new StreakStep(new Streak(months + 1, month), 0);
        if (gap <= freezesHeld) return new StreakStep(new Streak(months + 1, month), gap);
        return new StreakStep(new Streak(1, month), 0);
    }

    /** current 달 기준 화면에 보일 연속 개월 수. 지난달까지 이어졌으면 유지(이번 달에 1곳이면 계속), 그보다 오래면 0. */
    public int asOf(YearMonth current) {
        return asOf(current, 0);
    }

    /**
     * 보호권 freezesHeld 개를 가진 채 current 달에 보이는 연속 개월 수(8단계). 이번 달에 칠하면 빈 달을 보호권으로 메워 이어 갈 수 있으면
     * 유지, 아니면 0.
     */
    public int asOf(YearMonth current, int freezesHeld) {
        if (lastMonth == null || lastMonth.isAfter(current)) return 0;
        return emptyMonthsBefore(current) <= freezesHeld ? months : 0;
    }

    /** current 달에 칠하면 메워야 할 빈 달 수(= 쓰게 될 보호권 수, 8단계). 같은 달·바로 다음 달·기록 없음은 0. */
    public int emptyMonthsBefore(YearMonth current) {
        if (lastMonth == null || !current.isAfter(lastMonth)) return 0;
        return (int) lastMonth.until(current, ChronoUnit.MONTHS) - 1;
    }

    /**
     * 이번 달을 놓치면 끊길 수 있는 연속인지(12단계 스트릭 지키기 알림) — 아직 이번 달에 칠하지 않았고, 지금 칠하면 이어지는(보호권으로 빈 달을
     * 메울 수 있는) 연속이 있다.
     */
    public boolean atRiskIn(YearMonth current, int freezesHeld) {
        return !activeIn(current) && asOf(current, freezesHeld) > 0;
    }

    /** 이번 달을 놓치고 다음 달에 칠하면 메워야 할 빈 달 수(= 그때 필요한 보호권 수, 12단계). */
    public int freezesNeededIfMissed(YearMonth current) {
        return emptyMonthsBefore(current.plusMonths(1));
    }

    /** 이번 달에 이미 체크인했는지. */
    public boolean activeIn(YearMonth current) {
        return current.equals(lastMonth);
    }

    public int months() {
        return months;
    }

    public YearMonth lastMonth() {
        return lastMonth;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Streak streak && months == streak.months && Objects.equals(lastMonth, streak.lastMonth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(months, lastMonth);
    }

    @Override
    public String toString() {
        return "Streak[" + months + "개월, last=" + lastMonth + "]";
    }
}
