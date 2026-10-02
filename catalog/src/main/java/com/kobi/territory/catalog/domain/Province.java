package com.kobi.territory.catalog.domain;

import java.util.Objects;

/** 시·도(17개). 정복률 집계 단위. */
public record Province(String code, String name, String fullName, int displayOrder, int regionCount) {
    public Province {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }
}
