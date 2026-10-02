package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/** 지도 생성. 탐험은 빈 Territory로 시작(행 없음), 2단계부터 진행이 빈 Collection을 만든다. */
public record MapCreated(
    String mapId,
    String ownerId,
    String kind,
    String countryCode,
    Instant createdAt
) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return createdAt;
    }
}
