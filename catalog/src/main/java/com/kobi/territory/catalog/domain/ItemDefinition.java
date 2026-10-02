package com.kobi.territory.catalog.domain;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;

/**
 * 특산물 아이템 정의(참조 데이터). 지역 아이템의 itemId는 {@code region:{regionCode}}.
 * 상호·브랜드명은 일반명사화한 이름만 둔다(리스크 #9). 3단계에서 DB(item_definition)로 옮긴다.
 *
 * @param theme 배경(BG) 아이템의 풍경 테마, 그 외 null
 * @param look  엔진 룩(형태+색), 배경은 null
 */
public record ItemDefinition(
    String itemId,
    RegionCode regionCode,
    String name,
    String emoji,
    ItemSlot slot,
    Rarity tier,
    String theme,
    Look look
) {
    public ItemDefinition {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(tier, "tier");
    }

    public static String regionItemId(RegionCode code) {
        return "region:" + code.value();
    }

    public record Look(String type, String primary, String secondary) {}
}
