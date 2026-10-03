package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 익명 탐험가 from 이 로그인으로 계정 탐험가 into 에 병합됐다(claimExplorer — 사용자 확정 "기존 계정으로 병합").
 * from 은 이때부터 비활성(토큰 무효). 개인 지도 방문은 탐험이 비동기로 into 의 개인 지도로 옮기고 {@link VisitsMerged} 를 낸다.
 * outbox aggregate = ("Explorer", intoExplorerId).
 */
public record ExplorerMerged(String fromExplorerId, String intoExplorerId, String fromPersonalMapId, String intoPersonalMapId,
                             Instant mergedAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return mergedAt;
    }
}
