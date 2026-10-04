package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Objects;

/** 재방문 도장을 받은 지역 하나(inventory_revisit 행, 9단계) — 그 지역 특산물을 2회차 색 변형으로 그린다. @param markedAt 처음 받은 도장 시각 */
public record RevisitMark(RegionCode region, Instant markedAt) {
    public RevisitMark {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(markedAt, "markedAt");
    }
}
