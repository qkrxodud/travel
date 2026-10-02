package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Objects;

/** 재계산 재생용 방문 한 건(지도의 방문 이력 — 다른 멤버의 방문도 섞여 온다). */
public record ReplayVisit(ExplorerId visitor, CheckInGrant grant) {
    public ReplayVisit {
        Objects.requireNonNull(visitor, "visitor");
        Objects.requireNonNull(grant, "grant");
    }
}
