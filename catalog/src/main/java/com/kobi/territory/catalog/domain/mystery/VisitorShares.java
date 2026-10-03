package com.kobi.territory.catalog.domain.mystery;

import com.kobi.territory.common.model.RegionCode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 일급 컬렉션: 지역별 방문자 비율(일 1회 집계 region_stats 에서 온 값). 집계가 없거나 그 지역이 없으면 0(아무도 안 간 곳).
 */
public final class VisitorShares {

    private final Map<RegionCode, Double> ratioByRegion;

    private VisitorShares(Map<RegionCode, Double> ratioByRegion) {
        this.ratioByRegion = Map.copyOf(ratioByRegion);
    }

    public static VisitorShares none() {
        return new VisitorShares(Map.of());
    }

    public static VisitorShares of(Collection<RegionVisitors> counts) {
        Map<RegionCode, Double> ratios = new HashMap<>();
        counts.forEach(count -> ratios.put(count.region(), count.ratio()));
        return new VisitorShares(ratios);
    }

    public double ratioOf(RegionCode region) {
        return ratioByRegion.getOrDefault(region, 0.0);
    }
}
