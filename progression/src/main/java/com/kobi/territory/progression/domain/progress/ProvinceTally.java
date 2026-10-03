package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Objects;

/**
 * 탐험가 단위 지역(explorer_region) 집계 한 줄: 탐험가 × 시·도 → 활성 지역 수(5단계 — 친구 랭킹·상위 %·주 활동 시·도).
 * 활성 = 지금 어느 지도에든 방문이 살아 있는 지역(여러 지도에서 같은 지역을 칠해도 1).
 */
public record ProvinceTally(ExplorerId explorerId, String provinceCode, int regionCount) {
    public ProvinceTally {
        Objects.requireNonNull(explorerId, "explorerId");
        Objects.requireNonNull(provinceCode, "provinceCode");
        if (regionCount < 0) throw new IllegalArgumentException("regionCount=" + regionCount);
    }
}
