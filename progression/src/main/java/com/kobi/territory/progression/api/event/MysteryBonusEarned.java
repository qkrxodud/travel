package com.kobi.territory.progression.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 이번 주 미스터리 지역 보너스를 받음(8단계). 탐험가당 주당 한 번, 회수 없음. 소셜이 친구 소식을 만든다. aggregate = ExplorerProgress/explorerId.
 *
 * @param weekStart  그 주 월요일(ISO 날짜 — 주 id)
 * @param regionCode 그 주의 미스터리 지역
 */
public record MysteryBonusEarned(String explorerId, String weekStart, String regionCode, int xp, Instant earnedAt)
    implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return earnedAt;
    }
}
