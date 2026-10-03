package com.kobi.territory.wardrobe.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 초대한 탐험가가 받을 초대 보상(4단계). 초대받은 쪽 Inventory 가 그 쌍(초대자, 피초대자)을 처음 보상하며 낸다 — outbox
 * aggregate = Inventory/inviterId. 꾸미기(wardrobe.inventory)가 초대자 Inventory 하나만 고치는 트랜잭션에서 지급한다
 * (애그리거트 하나 원칙, ThemeRewardOwed 와 같은 방식). 아이템 단위로 멱등.
 *
 * @param joinedAt 합류 시각(한정 아이템 기간 판정 기준)
 */
public record InviteRewardOwed(String inviterId, String inviteeId, String mapId, Instant joinedAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return joinedAt;
    }
}
