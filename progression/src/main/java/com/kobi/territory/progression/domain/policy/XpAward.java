package com.kobi.territory.progression.domain.policy;

import java.util.Objects;

/** 보상 함수가 돌려준 XP 한 줄(출처 + 양). */
public record XpAward(XpSource source, int amount) {
    public XpAward {
        Objects.requireNonNull(source, "source");
    }
}
