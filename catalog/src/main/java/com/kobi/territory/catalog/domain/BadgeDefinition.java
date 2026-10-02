package com.kobi.territory.catalog.domain;

import java.util.Objects;

/** 뱃지 정의(12개). 뱃지는 한 번 얻으면 회수하지 않는다(진행 도메인 불변식). */
public record BadgeDefinition(String id, String ico, String name, String desc, BadgeCondition condition) {
    public BadgeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(condition, "condition");
    }
}
