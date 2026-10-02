package com.kobi.territory.catalog.domain.reward;

import java.util.Objects;

/** 보상 한 줄(출처 + XP). */
public record RewardLine(RewardSource source, int amount) {
    public RewardLine {
        Objects.requireNonNull(source, "source");
        if (amount < 0) throw new IllegalArgumentException("amount >= 0: " + amount);
    }
}
