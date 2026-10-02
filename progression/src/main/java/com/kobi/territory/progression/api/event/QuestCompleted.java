package com.kobi.territory.progression.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 퀘스트 보상 받기 완료. 진행이 구독해 XP(quest:{e}:{period}:{questId})와 상시 도전 칭호를 반영한다.
 *
 * @param period 'yyyy-MM'(월간) 또는 'ALL'(상시 도전)
 */
public record QuestCompleted(String explorerId, String period, String questId, int xp, Instant claimedAt)
    implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return claimedAt;
    }
}
