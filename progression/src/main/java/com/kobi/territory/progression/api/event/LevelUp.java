package com.kobi.territory.progression.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/** 레벨 상승(소셜 피드가 5단계에서 구독). 레벨이 내려가는 경우(취소로 XP 회수)는 발행하지 않는다. */
public record LevelUp(String explorerId, int level, long xp, Instant at) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return at;
    }
}
