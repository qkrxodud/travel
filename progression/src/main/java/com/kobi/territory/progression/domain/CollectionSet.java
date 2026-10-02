package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;
import java.util.Set;

/** 도감 세트 정의(진행 도메인이 보는 형태): id + 지역 집합. */
public record CollectionSet(String id, Set<RegionCode> regions) {
    public CollectionSet {
        Objects.requireNonNull(id, "id");
        regions = Set.copyOf(regions);
        if (regions.isEmpty()) throw new IllegalArgumentException("세트 지역 없음: " + id);
    }
}
