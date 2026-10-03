package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.Objects;

/** 초대받아 보상을 받은 기록 한 건(이 Inventory 주인 = 초대받은 쪽). 같은 초대자와는 한 번만(같은 쌍 1회). */
public record InvitationRecord(ExplorerId inviterId, String mapId, Instant rewardedAt) {
    public InvitationRecord {
        Objects.requireNonNull(inviterId, "inviterId");
        Objects.requireNonNull(mapId, "mapId");
        Objects.requireNonNull(rewardedAt, "rewardedAt");
    }
}
