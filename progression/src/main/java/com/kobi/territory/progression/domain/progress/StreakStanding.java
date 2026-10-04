package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.common.model.ExplorerId;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 탐험가 한 명의 연속 탐험 상태(읽기 전용 사실, 12단계) — 스트릭과 가진 보호권 수. 애그리거트를 통째로 불러오지 않고 저장소가 두 값만 돌려준다
 * ({@link StreakStandings}). 판단은 {@link Streak} 이 한다.
 */
public record StreakStanding(ExplorerId explorerId, Streak streak, int freezesHeld) {

    public StreakStanding {
        Objects.requireNonNull(explorerId, "explorerId");
        Objects.requireNonNull(streak, "streak");
        if (freezesHeld < 0) throw new IllegalArgumentException("보호권 수는 0 이상");
    }

    /** 이번 달 화면에 보일 연속 개월 수. */
    public int monthsAsOf(YearMonth current) {
        return streak.asOf(current, freezesHeld);
    }

    /** 이번 달을 놓치면 끊길 수 있는 연속인지. */
    public boolean atRiskIn(YearMonth current) {
        return streak.atRiskIn(current, freezesHeld);
    }

    /** 이번 달을 놓치면 다음 달에 필요한 보호권 수. */
    public int freezesNeededIfMissed(YearMonth current) {
        return streak.freezesNeededIfMissed(current);
    }

    public boolean checkedInIn(YearMonth current) {
        return streak.activeIn(current);
    }
}
