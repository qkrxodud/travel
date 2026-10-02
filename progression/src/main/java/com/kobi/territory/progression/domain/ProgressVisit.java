package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Objects;

/**
 * 진행이 보는 체크인 사실(RegionVisited 에서 application 이 옮긴 값).
 *
 * @param firstClaim 지도 안 선점 여부(이벤트 값 그대로) — 선점 보너스는 지도마다·수령자마다
 */
public record ProgressVisit(String mapId, RegionCode region, String provinceCode, Rarity rarity, Instant visitedAt,
                            boolean firstClaim) {
    public ProgressVisit {
        Objects.requireNonNull(mapId, "mapId");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(provinceCode, "provinceCode");
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(visitedAt, "visitedAt");
    }
}
