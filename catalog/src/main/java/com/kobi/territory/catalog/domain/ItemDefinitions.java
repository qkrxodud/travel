package com.kobi.territory.catalog.domain;

import com.kobi.territory.common.model.RegionCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 일급 컬렉션: 아이템 정의. itemId 유일, 지역 아이템(region:{code}) 조회, 지역마다 아이템이 있는지 검증. */
public final class ItemDefinitions {

    private final List<ItemDefinition> items;
    private final Map<String, ItemDefinition> byId;

    private ItemDefinitions(List<ItemDefinition> items) {
        Map<String, ItemDefinition> map = new LinkedHashMap<>();
        for (ItemDefinition i : items) {
            if (map.put(i.itemId(), i) != null) throw new IllegalStateException("아이템 중복: " + i.itemId());
        }
        this.items = List.copyOf(items);
        this.byId = map;
    }

    public static ItemDefinitions of(List<ItemDefinition> items) {
        return new ItemDefinitions(items);
    }

    public Optional<ItemDefinition> find(String itemId) {
        return Optional.ofNullable(byId.get(itemId));
    }

    public Optional<ItemDefinition> regionItem(RegionCode code) {
        return find(ItemDefinition.regionItemId(code));
    }

    public void requireCoverage(Regions regions) {
        regions.all().forEach(r -> regionItem(r.code())
            .orElseThrow(() -> new IllegalStateException("지역 아이템 누락: " + r.code())));
    }

    public List<ItemDefinition> all() {
        return items;
    }

    public int size() {
        return items.size();
    }
}
