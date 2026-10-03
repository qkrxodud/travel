package com.kobi.territory.wardrobe.domain.inventory;

import static com.kobi.territory.wardrobe.domain.Fixtures.EXPLORER;
import static com.kobi.territory.wardrobe.domain.Fixtures.SHARED_MAP;
import static com.kobi.territory.wardrobe.domain.Fixtures.T0;
import static com.kobi.territory.wardrobe.domain.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.wardrobe.domain.item.GrantKind;
import com.kobi.territory.wardrobe.domain.item.ItemSlot;
import com.kobi.territory.wardrobe.domain.item.ItemSpec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** D1: 초대 보상 — 처음 합류에만, 같은 쌍 1회, 셀프 초대·재가입·초대 아닌 합류는 없음, 재계산에도 유지(회수 없음). */
class InvitationTest {

    static final ExplorerId HOST = ExplorerId.of("22222222-2222-2222-2222-222222222222");
    static final ExplorerId OTHER_HOST = ExplorerId.of("33333333-3333-3333-3333-333333333333");
    static final ItemSpec TICKET = new ItemSpec("invite:guest-ticket", ItemSlot.BADGE, Rarity.RARE, GrantKind.INVITATION);
    static final String OTHER_MAP = "cccccccc-0000-0000-0000-000000000003";

    @Test
    void 초대받아_처음_합류하면_받고_초대자에게_보상을_돌린다_같은_쌍은_한_번만() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        InvitationOutcome first = inventory.acceptInvitation(new Invitation(HOST, SHARED_MAP, false, at(1)), List.of(TICKET));
        assertThat(first.change().granted()).extracting(OwnedItem::itemId).containsExactly("invite:guest-ticket");
        assertThat(first.inviterToReward()).contains(HOST);
        assertThat(inventory.find("invite:guest-ticket").orElseThrow().source()).isEqualTo(ItemSource.EVENT);

        // 재전달 · 같은 초대자가 다른 지도로 또 초대 — 같은 쌍은 1회
        assertThat(inventory.acceptInvitation(new Invitation(HOST, SHARED_MAP, false, at(1)), List.of(TICKET)).inviterToReward()).isEmpty();
        assertThat(inventory.acceptInvitation(new Invitation(HOST, OTHER_MAP, false, at(2)), List.of(TICKET)).inviterToReward()).isEmpty();
        assertThat(inventory.invitations().count()).isEqualTo(1);

        // 다른 초대자 — 새 쌍이라 초대자 보상은 돌리지만 피초대자 아이템은 이미 있어 새로 생기지 않는다
        InvitationOutcome another = inventory.acceptInvitation(new Invitation(OTHER_HOST, OTHER_MAP, false, at(3)), List.of(TICKET));
        assertThat(another.inviterToReward()).contains(OTHER_HOST);
        assertThat(another.change().granted()).isEmpty();
        assertThat(inventory.invitations().added()).hasSize(2);
    }

    @Test
    void 셀프_초대_재가입_초대_아닌_합류는_보상이_없다() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        assertThat(inventory.acceptInvitation(new Invitation(EXPLORER, SHARED_MAP, false, at(1)), List.of(TICKET)).change().changed())
            .isFalse();
        assertThat(inventory.acceptInvitation(new Invitation(HOST, SHARED_MAP, true, at(1)), List.of(TICKET)).inviterToReward())
            .isEmpty();
        assertThat(inventory.acceptInvitation(new Invitation(null, SHARED_MAP, false, at(1)), List.of(TICKET)).inviterToReward())
            .isEmpty();
        assertThat(inventory.owns("invite:guest-ticket")).isFalse();
        assertThat(inventory.invitations().count()).isZero();
    }

    @Test
    void 재계산해도_초대_보상과_기록은_남는다() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        inventory.acceptInvitation(new Invitation(HOST, SHARED_MAP, false, at(1)), List.of(TICKET));
        Inventory rebuilt = inventory.rebuildBase(Set.of(SHARED_MAP), at(5));
        assertThat(rebuilt.owns("invite:guest-ticket")).isTrue();
        assertThat(rebuilt.invitations().rewardedBy(HOST)).isTrue();
    }

    @Test
    void 계정_병합은_익명_탐험가의_재생_불가_아이템과_초대_기록을_옮기고_재생_가능한_것은_재계산에_맡긴다() {
        ExplorerId account = ExplorerId.of("44444444-4444-4444-4444-444444444444");
        Inventory anonymous = Inventory.empty(EXPLORER, T0);
        anonymous.acceptInvitation(new Invitation(HOST, SHARED_MAP, false, at(1)), List.of(TICKET));
        anonymous.grantRewards(List.of(new ItemSpec("set:jiri", ItemSlot.BG, Rarity.LEGEND, GrantKind.THEME_COMPLETE)), at(2));
        anonymous.grantRewards(List.of(new ItemSpec("event:gift", ItemSlot.HAT, Rarity.RARE, GrantKind.MANUAL)), at(3));

        Inventory into = Inventory.empty(account, T0);
        InventoryChange change = into.absorbMerged(anonymous, at(9));
        assertThat(change.granted()).extracting(OwnedItem::itemId).containsExactlyInAnyOrder("invite:guest-ticket", "event:gift");
        assertThat(into.find("invite:guest-ticket").orElseThrow().acquiredAt()).isEqualTo(at(1)); // 처음 얻은 시각 유지
        assertThat(into.owns("set:jiri")).as("테마 보상은 재계산이 만든다").isFalse();
        assertThat(into.invitations().rewardedBy(HOST)).isTrue();

        // 재전달 — 중복 없음
        assertThat(into.absorbMerged(anonymous, at(10)).granted()).isEmpty();
        assertThat(into.invitations().added()).hasSize(1);
    }
}
