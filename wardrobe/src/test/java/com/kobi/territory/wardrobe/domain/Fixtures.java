package com.kobi.territory.wardrobe.domain;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.wardrobe.domain.item.GrantKind;
import com.kobi.territory.wardrobe.domain.item.ItemSlot;
import com.kobi.territory.wardrobe.domain.item.ItemSpec;
import com.kobi.territory.wardrobe.domain.scene.StylePolicy;
import java.time.Instant;
import java.util.Map;

/** 꾸미기 도메인 테스트 공통 값. */
public final class Fixtures {

    public static final ExplorerId EXPLORER = ExplorerId.of("11111111-1111-1111-1111-111111111111");
    public static final String PERSONAL_MAP = "aaaaaaaa-0000-0000-0000-000000000001";
    public static final String SHARED_MAP = "bbbbbbbb-0000-0000-0000-000000000002";
    public static final Instant T0 = Instant.parse("2026-10-02T03:00:00Z");
    public static final StylePolicy STYLE = new StylePolicy(Map.of(Rarity.COMMON, 1, Rarity.RARE, 3, Rarity.LEGEND, 8));

    private Fixtures() {}

    public static Instant at(int minutes) {
        return T0.plusSeconds(60L * minutes);
    }

    public static ItemSpec regionItem(String code, ItemSlot slot, Rarity tier) {
        return new ItemSpec("region:" + code, slot, tier, GrantKind.REGION_VISIT);
    }

    public static ItemSpec setBackground(String setId) {
        return new ItemSpec("set:" + setId, ItemSlot.BG, Rarity.LEGEND, GrantKind.THEME_COMPLETE);
    }

    public static ItemSpec eventItem(String id, ItemSlot slot, Rarity tier) {
        return new ItemSpec("event:" + id, slot, tier, GrantKind.PERIOD_CHECK_IN);
    }
}
