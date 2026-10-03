package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Optional;

/**
 * 초대 합류 처리 결과. application 은 change 로 ItemGranted 를, hostOwed 가 있으면 초대자 보상(InviteRewardOwed)을 outbox 에
 * 적재한다(초대자 Inventory 는 다른 애그리거트 — 각자 트랜잭션).
 *
 * @param hostOwed 이번에 처음 보상한 쌍의 초대자, 보상 대상이 아니면 null
 */
public record InvitationOutcome(InventoryChange change, ExplorerId hostOwed) {

    public Optional<ExplorerId> inviterToReward() {
        return Optional.ofNullable(hostOwed);
    }
}
