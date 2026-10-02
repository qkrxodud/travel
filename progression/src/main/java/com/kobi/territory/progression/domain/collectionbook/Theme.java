package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;
import java.util.Set;

/**
 * 테마(설계 용어 "도감 세트"·CollectionSet): id + 지역 집합. 한 지도에서 지역을 모두 모으면 완성.
 * 공개 이벤트·테이블·API 는 기존 이름(SetCompleted·set_progress·sets)을 유지한다 — domain-model.md 매핑 참고.
 */
public final class Theme {

    private final String id;
    private final Set<RegionCode> regions;

    public Theme(String id, Set<RegionCode> regions) {
        this.id = Objects.requireNonNull(id, "id");
        this.regions = Set.copyOf(regions);
        if (this.regions.isEmpty()) throw new IllegalArgumentException("테마 지역 없음: " + id);
    }

    public String id() {
        return id;
    }

    public boolean includes(RegionCode region) {
        return regions.contains(region);
    }

    /** 모은 지역이 이 테마의 지역을 모두 덮는지. */
    public boolean completedBy(Set<RegionCode> collected) {
        return collected.containsAll(regions);
    }
}
