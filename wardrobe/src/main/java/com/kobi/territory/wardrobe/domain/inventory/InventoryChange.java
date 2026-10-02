package com.kobi.territory.wardrobe.domain.inventory;

import java.time.Instant;
import java.util.List;

/** Inventory 커맨드 한 번의 결과. application 이 이것으로 공개 이벤트(ItemGranted·ItemRevoked)를 outbox 에 적재한다. */
public record InventoryChange(List<OwnedItem> granted, List<OwnedItem> revoked, Instant at) {

    public InventoryChange {
        granted = List.copyOf(granted);
        revoked = List.copyOf(revoked);
    }

    static InventoryChange none(Instant at) {
        return new InventoryChange(List.of(), List.of(), at);
    }

    public boolean changed() {
        return !granted.isEmpty() || !revoked.isEmpty();
    }
}
