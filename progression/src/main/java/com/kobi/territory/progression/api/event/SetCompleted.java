package com.kobi.territory.progression.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 도감 세트 완성(지도 단위, 단 한 번). 진행(보너스 XP·칭호)과 꾸미기(세트 배경 전원 지급, 3단계)가 구독한다.
 *
 * @param explorerId 완성시킨 체크인의 탐험가 — 2단계 보너스 XP 수령자(공유 지도 전원 지급 여부는 3단계에서 결정)
 */
public record SetCompleted(String mapId, String setId, String explorerId, Instant completedAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return completedAt;
    }
}
