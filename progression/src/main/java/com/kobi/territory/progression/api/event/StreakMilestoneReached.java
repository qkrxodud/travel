package com.kobi.territory.progression.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 연속 탐험 마일스톤에 처음 닿음(8단계). 탐험가당 마일스톤당 한 번 — 끊겼다 다시 쌓아도 다시 나지 않는다. 꾸미기가 한정 아이템
 * (STREAK_MILESTONE)을, 소셜이 친구 소식을 만든다. aggregate = ExplorerProgress/explorerId.
 *
 * @param months 마일스톤 개월 수(3·6·12·24 — 카탈로그 streak-rules.json)
 * @param xp     받은 XP
 */
public record StreakMilestoneReached(String explorerId, int months, int xp, Instant reachedAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return reachedAt;
    }
}
