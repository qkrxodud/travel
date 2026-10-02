package com.kobi.territory.progression.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/** 뱃지 획득(추가만, 회수 없음). */
public record BadgeEarned(String explorerId, String badgeId, Instant at) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return at;
    }
}
