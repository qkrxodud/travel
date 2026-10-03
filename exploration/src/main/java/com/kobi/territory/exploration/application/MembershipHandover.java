package com.kobi.territory.exploration.application;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 탐험 내부 이벤트(공개 계약 아님): 병합(ExplorerMerged) 때 from 이 멤버인 공유 지도마다 하나씩 낸다 — 지도 애그리거트마다 자기
 * 트랜잭션에서 자리를 정리하게(한 트랜잭션 한 애그리거트). 구독자 exploration.expedition-map. outbox aggregate = ("ExpeditionMap", mapId).
 * 결과는 공개 이벤트 MemberJoined·MemberLeft(·MemberPurged)로 나간다.
 */
public record MembershipHandover(String mapId, String fromExplorerId, String intoExplorerId, Instant requestedAt)
    implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return requestedAt;
    }
}
