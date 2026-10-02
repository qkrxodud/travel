package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import com.kobi.territory.common.model.Rarity;
import java.time.Instant;

/**
 * 체크인 취소(물리 삭제). 하류는 지역 아이템·기본 XP만 되돌린다(세트·뱃지·퀘스트 보상 유지).
 *
 * @param wasClaim  취소된 방문이 그 지역의 선점이었는지(공유 지도에서 선점 이전 판단용, 3단계)
 * @param remaining 취소 후 이 멤버의 이 지도 기준 영토 수
 */
public record VisitCancelled(
    String explorerId,
    String mapId,
    String regionCode,
    Rarity rarity,
    String provinceCode,
    boolean wasClaim,
    int remaining,
    Instant cancelledAt
) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return cancelledAt;
    }
}
