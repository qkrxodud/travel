package com.kobi.territory.common.event;

import java.time.Instant;

public interface DomainEvent {
    Instant occurredAt();
}
