package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import com.kobi.territory.common.model.Rarity;
import java.time.Instant;

/**
 * 선점 이전 — 선점자의 체크인 취소나 탈퇴로, 그 지역을 다음으로 체크인한 멤버가 선점자가 됐다(§5).
 * 진행이 새 선점자에게 선점 보너스(refId claim:{mapId}:{code}:{explorerId})를 지급한다. 떠난 사람 보너스는 회수하지 않는다.
 *
 * @param explorerId 새 선점자(보너스 수령자)
 * @param reason     CANCELLED(체크인 취소) | LEFT(탈퇴)
 */
public record ClaimTransferred(String mapId, String regionCode, Rarity rarity, String provinceCode, String fromExplorerId,
                               String explorerId, String reason, Instant transferredAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return transferredAt;
    }
}
