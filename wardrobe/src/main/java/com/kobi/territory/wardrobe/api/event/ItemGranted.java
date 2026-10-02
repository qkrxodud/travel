package com.kobi.territory.wardrobe.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 아이템 획득(공개 이벤트, outbox aggregate = Inventory/explorerId). 장면(Scene)이 구독해 자동 착용한다
 * (슬롯·희귀도는 카탈로그 아이템 정의에서 읽는다).
 *
 * @param source REGION | SET_REWARD | EVENT
 */
public record ItemGranted(String explorerId, String itemId, String source, Instant acquiredAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return acquiredAt;
    }
}
