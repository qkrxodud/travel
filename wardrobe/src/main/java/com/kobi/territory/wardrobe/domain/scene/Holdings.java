package com.kobi.territory.wardrobe.domain.scene;

import java.util.Collection;
import java.util.Set;

/**
 * 일급 컬렉션: 착용 검증에 쓰는 보유 아이템 id(같은 컨텍스트의 Inventory 가 가진 것 — 장면은 Inventory 내부를 모르고 id 만 받는다).
 */
public final class Holdings {

    private final Set<String> itemIds;

    private Holdings(Collection<String> itemIds) {
        this.itemIds = Set.copyOf(itemIds);
    }

    public static Holdings of(Collection<String> itemIds) {
        return new Holdings(itemIds);
    }

    public boolean owns(String itemId) {
        return itemIds.contains(itemId);
    }
}
