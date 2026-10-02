package com.kobi.territory.wardrobe.domain.item;

import com.kobi.territory.common.model.Rarity;
import java.util.Objects;

/**
 * 꾸미기가 아는 아이템 사양(카탈로그 ItemDefinition 에서 필요한 것만 — 생김새는 여기 없다).
 */
public record ItemSpec(String itemId, ItemSlot slot, Rarity tier, GrantKind grantKind) {
    public ItemSpec {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(grantKind, "grantKind");
    }

    /** 희귀도가 other 보다 높은지(자동 착용 판단). */
    public boolean rarerThan(ItemSpec other) {
        return tier.compareTo(other.tier) > 0;
    }
}
