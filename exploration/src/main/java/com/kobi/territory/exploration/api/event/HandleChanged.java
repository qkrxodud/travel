package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 탐험가 handle 이 정해지거나(최초 로그인 — previousHandle null) 바뀌었다. 공개 프로필 주소(/u/{handle})가 바뀐다.
 * outbox aggregate = ("Explorer", explorerId).
 */
public record HandleChanged(String explorerId, String previousHandle, String handle, Instant changedAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return changedAt;
    }
}
