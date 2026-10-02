package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/** 지도장이 방문에 이의(disputed)를 표시하거나 해제했다 — 지도 내 랭킹 집계 제외용(5단계). 개인 영토·전체 랭킹 영향 없음. */
public record VisitDisputeChanged(String mapId, String regionCode, String explorerId, boolean disputed, String changedBy,
                                  Instant at) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return at;
    }
}
