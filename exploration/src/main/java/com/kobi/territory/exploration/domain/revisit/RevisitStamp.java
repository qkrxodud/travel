package com.kobi.territory.exploration.domain.revisit;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Objects;

/**
 * 재방문 도장 한 개(revisit_stamp 행) — 지역·연도당 하나, 지우지 않는다.
 *
 * @param year      도장 연도(처리 시각의 서비스 시간대 연도)
 * @param stampedAt 처리 시각
 */
public record RevisitStamp(RegionCode region, int year, Instant stampedAt) {
    public RevisitStamp {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(stampedAt, "stampedAt");
    }

    boolean sameAs(RegionCode otherRegion, int otherYear) {
        return region.equals(otherRegion) && year == otherYear;
    }
}
