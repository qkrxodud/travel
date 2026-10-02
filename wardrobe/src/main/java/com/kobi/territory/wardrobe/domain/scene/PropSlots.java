package com.kobi.territory.wardrobe.domain.scene;

import com.kobi.territory.wardrobe.domain.WardrobeError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** 일급 컬렉션: 장식 칸(최대 {@link #MAX}개, 순서 = 장면 배치 순서, 같은 아이템 중복 불가). */
public final class PropSlots {

    /** 장식 칸 수 — 설계 불변식(§2-6 PropSlots ≤3, 화면의 배치 자리 3곳). */
    public static final int MAX = 3;

    private final List<String> itemIds;

    private PropSlots(List<String> itemIds) {
        if (itemIds.size() > MAX) throw WardrobeError.TOO_MANY_PROPS.exception(MAX);
        if (new HashSet<>(itemIds).size() != itemIds.size()) {
            throw WardrobeError.DUPLICATE_PROP.exception(String.join(",", itemIds));
        }
        this.itemIds = List.copyOf(itemIds);
    }

    public static PropSlots empty() {
        return new PropSlots(List.of());
    }

    public static PropSlots of(List<String> itemIds) {
        return new PropSlots(itemIds);
    }

    public boolean full() {
        return itemIds.size() >= MAX;
    }

    public boolean contains(String itemId) {
        return itemIds.contains(itemId);
    }

    PropSlots with(String itemId) {
        List<String> next = new ArrayList<>(itemIds);
        next.add(itemId);
        return new PropSlots(next);
    }

    PropSlots without(String itemId) {
        return new PropSlots(itemIds.stream().filter(id -> !id.equals(itemId)).toList());
    }

    public List<String> itemIds() {
        return itemIds;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PropSlots props && itemIds.equals(props.itemIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemIds);
    }
}
