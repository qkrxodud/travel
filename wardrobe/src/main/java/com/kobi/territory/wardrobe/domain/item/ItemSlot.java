package com.kobi.territory.wardrobe.domain.item;

import java.util.Optional;

/**
 * 아이템 정의의 슬롯(카탈로그 Published Language 와 같은 이름). PROP 은 장식 칸에, 나머지는 같은 이름의 착용 슬롯에 들어간다.
 */
public enum ItemSlot {
    HAT, HAND, BADGE, BAG, PET, BG, PROP;

    /** 착용 슬롯. 장식(PROP)은 없음. */
    public Optional<EquipSlot> equipSlot() {
        return this == PROP ? Optional.empty() : Optional.of(EquipSlot.valueOf(name()));
    }

    public boolean prop() {
        return this == PROP;
    }
}
