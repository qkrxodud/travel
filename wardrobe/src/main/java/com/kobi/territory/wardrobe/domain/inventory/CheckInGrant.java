package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.wardrobe.domain.item.ItemSpec;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 체크인 한 번(RegionVisited)의 지급 입력. items = 카탈로그가 판정한 이번 체크인의 지급 아이템(지역 특산물 + 이슈 아이템).
 *
 * @param generation 같은 (지도, 지역, 멤버)의 체크인 회차(세대)
 */
public record CheckInGrant(String mapId, RegionCode region, long generation, List<ItemSpec> items, Instant at) {
    public CheckInGrant {
        Objects.requireNonNull(mapId, "mapId");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(at, "at");
        items = List.copyOf(items);
    }
}
