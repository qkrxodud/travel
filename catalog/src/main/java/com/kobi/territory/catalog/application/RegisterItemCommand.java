package com.kobi.territory.catalog.application;

import com.kobi.territory.catalog.domain.item.GrantRule;
import com.kobi.territory.catalog.domain.item.ItemDefinition;
import com.kobi.territory.catalog.domain.item.ItemSlot;
import com.kobi.territory.catalog.domain.item.ValidPeriod;
import com.kobi.territory.common.model.Rarity;
import java.time.Instant;
import java.time.LocalDate;

/** 운영 아이템 추가 커맨드(POST /admin/items). 형식 검증은 도메인 생성자(ItemDefinition·GrantRule·ValidPeriod)가 한다. */
public record RegisterItemCommand(String itemId, String name, String emoji, ItemSlot slot, Rarity tier, String theme,
                                  String lookType, String colorPrimary, String colorSecondary, GrantRule.Type grantRule,
                                  String grantRef, LocalDate validFrom, LocalDate validTo) {

    ItemDefinition toDefinition(Instant createdAt) {
        ItemDefinition.Look look = lookType == null && colorPrimary == null && colorSecondary == null ? null
            : new ItemDefinition.Look(lookType, colorPrimary, colorSecondary);
        return new ItemDefinition(itemId, name, emoji, slot, tier, theme, look, GrantRule.of(grantRule, grantRef),
            new ValidPeriod(validFrom, validTo), createdAt);
    }
}
