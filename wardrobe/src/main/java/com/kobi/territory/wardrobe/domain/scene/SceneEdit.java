package com.kobi.territory.wardrobe.domain.scene;

import com.kobi.territory.wardrobe.domain.item.EquipSlot;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 장면 편집(PUT /scene) 한 번. 비어 있는 부분은 그대로 둔다.
 *
 * @param gender  바꿀 성별(null 이면 유지)
 * @param equip   슬롯 → 입을 아이템
 * @param unequip 벗을 슬롯(equip 보다 먼저 적용)
 * @param props   장식 칸 전체(순서대로, null 이면 유지)
 */
public record SceneEdit(Gender gender, Map<EquipSlot, String> equip, Set<EquipSlot> unequip, List<String> props) {
    public SceneEdit {
        equip = equip == null ? Map.of() : Map.copyOf(equip);
        unequip = unequip == null ? Set.of() : Set.copyOf(unequip);
        props = props == null ? null : List.copyOf(props);
    }
}
