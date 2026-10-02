package com.kobi.territory.progression.domain;

import java.util.Objects;

/** 뱃지 정의(진행 도메인이 보는 형태): id + 조건. */
public record Badge(String id, BadgeRule rule) {
    public Badge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rule, "rule");
    }
}
