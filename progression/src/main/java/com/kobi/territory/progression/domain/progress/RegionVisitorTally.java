package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;

/** 지역 하나를 활성으로 가진 탐험가 수(5단계 — 지역별 방문자 비율 region_stats 배치). */
public record RegionVisitorTally(RegionCode regionCode, int visitorCount) {
    public RegionVisitorTally {
        Objects.requireNonNull(regionCode, "regionCode");
        if (visitorCount < 0) throw new IllegalArgumentException("visitorCount=" + visitorCount);
    }
}
