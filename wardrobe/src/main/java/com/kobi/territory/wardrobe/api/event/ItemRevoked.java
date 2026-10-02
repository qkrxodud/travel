package com.kobi.territory.wardrobe.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/** 아이템 회수(지역 방문이 모든 지도에서 취소됨, outbox aggregate = Inventory/explorerId). 장면이 구독해 입고 있으면 벗긴다. */
public record ItemRevoked(String explorerId, String itemId, Instant revokedAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return revokedAt;
    }
}
