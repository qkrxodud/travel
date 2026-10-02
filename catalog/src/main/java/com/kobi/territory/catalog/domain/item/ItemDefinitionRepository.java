package com.kobi.territory.catalog.domain.item;

import java.util.Optional;

/** 아이템 정의 저장소(item_definition). 운영이 수시로 추가하므로 시작 시 메모리에 고정하지 않고 조회마다 읽는다. */
public interface ItemDefinitionRepository {

    ItemDefinitions loadAll();

    Optional<ItemDefinition> find(String itemId);

    void add(ItemDefinition item);
}
