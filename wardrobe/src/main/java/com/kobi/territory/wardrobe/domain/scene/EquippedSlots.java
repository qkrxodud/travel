package com.kobi.territory.wardrobe.domain.scene;

import com.kobi.territory.wardrobe.domain.item.EquipSlot;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 일급 컬렉션: 슬롯별 착용 아이템(한 슬롯에 하나). */
public final class EquippedSlots {

    private final EnumMap<EquipSlot, String> bySlot = new EnumMap<>(EquipSlot.class);

    private EquippedSlots(Map<EquipSlot, String> restored) {
        restored.forEach((slot, itemId) -> {
            if (itemId != null) bySlot.put(slot, itemId);
        });
    }

    public static EquippedSlots empty() {
        return new EquippedSlots(Map.of());
    }

    public static EquippedSlots of(Map<EquipSlot, String> restored) {
        return new EquippedSlots(restored);
    }

    public Optional<String> itemAt(EquipSlot slot) {
        return Optional.ofNullable(bySlot.get(slot));
    }

    /** @return 바뀌었는지 */
    boolean put(EquipSlot slot, String itemId) {
        return !itemId.equals(bySlot.put(slot, itemId));
    }

    /** @return 바뀌었는지 */
    boolean clear(EquipSlot slot) {
        return bySlot.remove(slot) != null;
    }

    /** 이 아이템을 입은 슬롯을 비운다. @return 바뀌었는지 */
    boolean takeOff(String itemId) {
        return bySlot.values().removeIf(itemId::equals);
    }

    /** 착용 아이템 id(슬롯 순서). */
    public List<String> itemIds() {
        return List.copyOf(bySlot.values());
    }
}
