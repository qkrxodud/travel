package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 병합(claimExplorer)으로 공유 지도의 멤버 from(익명 탐험가)이 into(계정 탐험가)로 바뀌었다(사용자 결정 Q2). 탈퇴(MemberLeft)가
 * 아니다 — from 의 방문·선점은 선점 순서를 그대로 유지한 채 into 의 것이 된다(탐험이 이 이벤트로 영토를 재귀속). 다른 멤버의 선점은
 * 바뀌지 않는다. 진행·인벤토리는 재계산 예약으로 맞춘다. outbox aggregate = ("ExpeditionMap", mapId).
 */
public record MemberReassigned(String mapId, String fromExplorerId, String intoExplorerId, Instant reassignedAt)
    implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return reassignedAt;
    }
}
