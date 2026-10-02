package com.kobi.territory.wardrobe.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 장면(착용·성별·장식)이 바뀜(outbox aggregate = Scene/explorerId). 4단계 공유(ShareCard)가 구독해 카드 스냅샷을 무효화한다
 * — 지금은 발행만.
 */
public record SceneChanged(String explorerId, Instant changedAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return changedAt;
    }
}
