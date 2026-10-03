package com.kobi.territory.progression.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 시·도 정복(8단계): 탐험가 단위로 한 시·도의 현행 지역(폐지 지역 제외)을 모두 칠함. 탐험가당 시·도당 한 번 — 취소해도 회수 없고,
 * 다시 100%가 돼도 다시 나지 않는다. 꾸미기가 그 시·도 대표 장식(PROVINCE_COMPLETE)을, 소셜이 친구 소식을 만든다.
 * aggregate = ExplorerProgress/explorerId.
 */
public record ProvinceConquered(String explorerId, String provinceCode, int xp, Instant conqueredAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return conqueredAt;
    }
}
