package com.kobi.territory.wardrobe.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 세트 보상을 따로 받아야 하는 지도 멤버(완성 직후 합류해 수령자 목록에 없던 사람, QA P3-1). outbox aggregate = Inventory/explorerId —
 * 꾸미기(wardrobe.inventory)가 그 탐험가 Inventory 하나만 고치는 트랜잭션에서 지급한다(애그리거트 하나 원칙, QA P3-R2-5).
 *
 * @param completedAt 테마 완성 시각(보상 기간 판정 기준)
 */
public record ThemeRewardOwed(String mapId, String setId, String explorerId, Instant completedAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return completedAt;
    }
}
