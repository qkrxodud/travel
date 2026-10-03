package com.kobi.territory.progression.domain.progress;

import java.util.Objects;

/** 한 달 체크인으로 바뀐 스트릭과 그때 쓴 보호권 수(8단계). */
public record StreakStep(Streak streak, int freezesUsed) {
    public StreakStep {
        Objects.requireNonNull(streak, "streak");
        if (freezesUsed < 0) throw new IllegalArgumentException("freezesUsed >= 0");
    }
}
