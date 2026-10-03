package com.kobi.territory.sharing.domain.showcase;

import com.kobi.territory.common.model.Rarity;
import java.util.Objects;

/** 공개 정보에 쓰는 지역 정의(카탈로그에서 필요한 것만). */
public record RegionInfo(String code, String name, String provinceCode, String provinceName, Rarity rarity) {
    public RegionInfo {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(provinceCode, "provinceCode");
        Objects.requireNonNull(provinceName, "provinceName");
        Objects.requireNonNull(rarity, "rarity");
    }

    public boolean legend() {
        return rarity == Rarity.LEGEND;
    }
}
