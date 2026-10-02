package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;

/**
 * 탐험 도메인이 보는 지역 정보. 카탈로그(상류)의 값을 application 계층이 이 VO로 옮겨 넣는다
 * (domain은 다른 모듈의 api를 참조하지 않는다). retired: 행정구역 개편으로 폐지된 지역(retiredAt≠null) — 새 체크인 불가.
 */
public record RegionSnapshot(RegionCode code, Rarity rarity, String provinceCode, boolean retired) {
    public RegionSnapshot {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(provinceCode, "provinceCode");
    }

    /** 현행(폐지되지 않은) 지역. */
    public RegionSnapshot(RegionCode code, Rarity rarity, String provinceCode) {
        this(code, rarity, provinceCode, false);
    }
}
