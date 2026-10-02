package com.kobi.territory.catalog.domain.region;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 지역(시·군·구) 참조 데이터. 애그리거트가 아니다.
 * 행정구역 개편 대응(리스크 #8): 폐지 코드는 삭제하지 않고 {@code retiredAt}·{@code replacedBy}로 숨긴다.
 */
public record Region(
    RegionCode code,
    String name,
    String provinceCode,
    Rarity rarity,
    String countryCode,
    int version,
    RegionCode replacedBy,
    LocalDate retiredAt
) {
    public Region {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(provinceCode, "provinceCode");
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(countryCode, "countryCode");
        if (version < 1) throw new IllegalArgumentException("version >= 1: " + code);
        if (!code.countryCode().equals(countryCode)) {
            throw new IllegalArgumentException("국가 코드 불일치: " + code + " / " + countryCode);
        }
    }

    public boolean active() {
        return retiredAt == null;
    }
}
