package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/** 탈퇴 유예가 끝나 멤버 기록이 지워졌다. 탐험이 숨긴 방문을 하드 삭제한다. */
public record MemberPurged(String mapId, String explorerId, Instant purgedAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return purgedAt;
    }
}
