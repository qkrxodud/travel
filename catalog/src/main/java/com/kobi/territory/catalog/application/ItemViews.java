package com.kobi.territory.catalog.application;

import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.catalog.domain.item.ItemDefinition;

/** 아이템 정의 → 공개 view 매핑(표현 변환만). */
final class ItemViews {

    private ItemViews() {}

    static ItemView of(ItemDefinition item) {
        return new ItemView(item.itemId(), item.regionCode() == null ? null : item.regionCode().value(), item.name(),
            item.emoji(), item.slot().name(), item.tier(), item.theme(),
            item.look() == null ? null : new ItemView.Look(item.look().type(), item.look().primary(), item.look().secondary()),
            item.grantRule().type().name(), item.grantRule().ref(), item.validPeriod().from(), item.validPeriod().to());
    }
}
