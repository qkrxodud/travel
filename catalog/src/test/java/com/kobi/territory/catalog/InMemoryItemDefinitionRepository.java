package com.kobi.territory.catalog;

import com.kobi.territory.catalog.domain.item.ItemDefinition;
import com.kobi.territory.catalog.domain.item.ItemDefinitionRepository;
import com.kobi.territory.catalog.domain.item.ItemDefinitions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 테스트용 아이템 정의 저장소(메모리). 실제 데이터는 DB(V3_1 이관) — 앱 통합 테스트가 검증한다. */
final class InMemoryItemDefinitionRepository implements ItemDefinitionRepository {

    private final List<ItemDefinition> items = new ArrayList<>();

    InMemoryItemDefinitionRepository(List<ItemDefinition> initial) {
        items.addAll(initial);
    }

    static InMemoryItemDefinitionRepository empty() {
        return new InMemoryItemDefinitionRepository(List.of());
    }

    @Override
    public ItemDefinitions loadAll() {
        return ItemDefinitions.of(items);
    }

    @Override
    public Optional<ItemDefinition> find(String itemId) {
        return items.stream().filter(item -> item.itemId().equals(itemId)).findFirst();
    }

    @Override
    public void add(ItemDefinition item) {
        items.add(item);
    }
}
