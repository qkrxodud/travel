package com.kobi.territory.sharing.domain.showcase;

import java.util.Objects;

/** 시·도(표시 순서, 지역 수). */
public record ProvinceInfo(String code, String name, int regionCount) {
    public ProvinceInfo {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        if (regionCount < 0) throw new IllegalArgumentException("regionCount=" + regionCount);
    }
}
