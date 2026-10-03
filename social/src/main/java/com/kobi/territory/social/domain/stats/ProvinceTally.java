package com.kobi.territory.social.domain.stats;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Objects;

/** 탐험가 × 시·도 → 활성 지역 수(진행 공개 Query explorer_region 집계에서 옮긴 값). */
public record ProvinceTally(ExplorerId explorerId, String provinceCode, int regionCount) {
    public ProvinceTally {
        Objects.requireNonNull(explorerId, "explorerId");
        Objects.requireNonNull(provinceCode, "provinceCode");
        if (regionCount < 0) throw new IllegalArgumentException("regionCount=" + regionCount);
    }
}
