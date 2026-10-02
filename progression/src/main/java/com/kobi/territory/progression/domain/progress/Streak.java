package com.kobi.territory.progression.domain.progress;

import java.time.YearMonth;
import java.util.Objects;

/**
 * 탐험 스트릭 — 매달 새 지역 1곳 이상 체크인하면 연속이 이어진다. 처리 시각(visitedAt) 기준이라 소급이 없다.
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

    /** current 달 기준 화면에 보일 연속 개월 수. 지난달까지 이어졌으면 유지(이번 달에 1곳이면 계속), 그보다 오래면 0. */
    public int asOf(YearMonth current) {
        if (lastMonth == null) return 0;
        return lastMonth.equals(current) || lastMonth.equals(current.minusMonths(1)) ? months : 0;
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
