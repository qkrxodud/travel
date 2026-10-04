package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Objects;

/** 탐험이 기록한 재방문 도장 한 개(재계산 복구 입력, 9단계). @param at 도장 받은 시각 */
public record StampFact(RegionCode region, int year, Instant at) {
    public StampFact {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(at, "at");
    }
}
