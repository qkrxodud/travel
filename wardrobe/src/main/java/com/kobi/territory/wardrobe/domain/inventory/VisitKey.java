package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;
import java.util.Set;

/** 방문 하나의 자리(지역, 지도) — 체크인으로 받은 아이템의 근거(basis)와 방문 흔적(VisitTrace)의 키. */
public record VisitKey(RegionCode region, String mapId) {
    public VisitKey {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(mapId, "mapId");
    }

    public boolean on(Set<String> mapIds) {
        return mapIds.contains(mapId);
    }
}
