package com.kobi.territory.catalog.domain;

import java.util.Objects;

/** 레벨 칭호: 이 레벨에 도달하면 얻는다. */
public record LevelTitle(int level, String name) {
    public LevelTitle {
        if (level < 1) throw new IllegalArgumentException("level >= 1");
        Objects.requireNonNull(name, "name");
    }
}
