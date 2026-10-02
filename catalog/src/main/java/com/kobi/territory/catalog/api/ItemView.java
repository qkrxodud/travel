package com.kobi.territory.catalog.api;

import com.kobi.territory.common.model.Rarity;

/** 아이템 정의 공개 표현. slot: HAND|BADGE|HAT|BAG|PET|BG|PROP */
public record ItemView(String itemId, String regionCode, String name, String emoji, String slot, Rarity tier, String theme,
                       Look look) {
    public record Look(String type, String primary, String secondary) {}
}
