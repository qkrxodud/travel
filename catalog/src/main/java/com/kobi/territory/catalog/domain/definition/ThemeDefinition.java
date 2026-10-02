package com.kobi.territory.catalog.domain.definition;

import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import java.util.Objects;

/**
 * 도감 세트 정의(참조 데이터). 정의된 지역을 한 지도에서 모두 모으면 완성 — 보상은 칭호·보너스 XP·세트 배경(3단계).
 *
 * @param background 완성 시 멤버 전원에게 주는 세트 배경 아이템 정보(itemId = set:{id}, 3단계에서 지급)
 */
public record ThemeDefinition(String id, String name, String desc, String title, List<RegionCode> regions,
                                      Background background) {
    public ThemeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(title, "title");
        regions = List.copyOf(regions);
        if (regions.isEmpty() || regions.stream().distinct().count() != regions.size()) {
            throw new IllegalStateException("세트 지역이 비었거나 중복: " + id);
        }
    }

    public record Background(String emoji, String name, String theme) {}
}
