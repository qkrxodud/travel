package com.kobi.territory.catalog.domain.mystery;

import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;

/** 한 지역의 방문자 수와 모집단(일 1회 집계 값). */
public record RegionVisitors(RegionCode region, int visitors, int population) {
    public RegionVisitors {
        Objects.requireNonNull(region, "region");
        if (visitors < 0 || population < 0) throw new IllegalArgumentException("방문자·모집단은 0 이상");
    }

    /** 방문자 비율(0~1). 모집단이 0이면 0. */
    public double ratio() {
        return population == 0 ? 0 : (double) visitors / population;
    }
}
