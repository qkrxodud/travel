package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Objects;

/** 탐험이 기록한 다녀온 가고 싶은 곳 하나(재계산 복구 입력, 9단계). @param at 다녀온 시각 */
public record WishFact(RegionCode region, Instant at) {
    public WishFact {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(at, "at");
    }
}
