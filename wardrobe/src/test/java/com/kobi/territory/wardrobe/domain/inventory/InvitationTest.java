package com.kobi.territory.wardrobe.domain.inventory;

import static com.kobi.territory.wardrobe.domain.Fixtures.GUEST_TICKET;
import static com.kobi.territory.wardrobe.domain.Fixtures.HOST;
import static com.kobi.territory.wardrobe.domain.Fixtures.ME;
import static com.kobi.territory.wardrobe.domain.Fixtures.OTHER_HOST;
import static com.kobi.territory.wardrobe.domain.Fixtures.OTHER_MAP;
import static com.kobi.territory.wardrobe.domain.Fixtures.SHARED_MAP;
import static com.kobi.territory.wardrobe.domain.Fixtures.at;
import static com.kobi.territory.wardrobe.domain.Fixtures.bag;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 4단계 초대 보상(§7) — 처음 합류·같은 쌍 1회·셀프 아님·재가입 아님. */
@DisplayName("초대 보상")
class InvitationTest {

    static Invitation invitedBy(ExplorerId inviter, String mapId, int minute) {
        return new Invitation(inviter, mapId, false, at(minute));
    }

    static InvitationOutcome accept(Inventory inventory, Invitation invitation) {
        return inventory.acceptInvitation(invitation, List.of(GUEST_TICKET));
    }

    @Nested
    @DisplayName("초대받아 처음 합류하면")
    class FirstJoin {

        @Test
        @DisplayName("초대받은 사람이 보상 아이템을 받는다")
        void guestReceivesItem() {
            InvitationOutcome outcome = accept(bag(), invitedBy(HOST, SHARED_MAP, 1));

            assertThat(outcome.change().granted()).extracting(OwnedItem::itemId).containsExactly("invite:guest-ticket");
        }

        @Test
        @DisplayName("초대한 사람에게도 보상을 돌린다")
        void inviterIsRewarded() {
            assertThat(accept(bag(), invitedBy(HOST, SHARED_MAP, 1)).inviterToReward()).contains(HOST);
        }

        @Test
        @DisplayName("초대 보상 아이템은 거둬 가지 않는 이벤트 아이템이다")
        void rewardIsEventItem() {
            Inventory inventory = bag();
            accept(inventory, invitedBy(HOST, SHARED_MAP, 1));

            assertThat(inventory.find("invite:guest-ticket").orElseThrow().source()).isEqualTo(ItemSource.EVENT);
        }
    }

    @Nested
    @DisplayName("같은 사람에게 다시 초대받으면")
    class SameInviter {

        @Test
        @DisplayName("같은 합류 소식이 다시 와도 초대한 사람 보상은 한 번만이다")
        void sameJoinTwice() {
            Inventory inventory = bag();
            accept(inventory, invitedBy(HOST, SHARED_MAP, 1));

            assertThat(accept(inventory, invitedBy(HOST, SHARED_MAP, 1)).inviterToReward()).isEmpty();
        }

        @Test
        @DisplayName("다른 지도로 또 초대받아도 같은 두 사람 사이 보상은 한 번만이다")
        void samePairOtherMap() {
            Inventory inventory = bag();
            accept(inventory, invitedBy(HOST, SHARED_MAP, 1));

            assertThat(accept(inventory, invitedBy(HOST, OTHER_MAP, 2)).inviterToReward()).isEmpty();
            assertThat(inventory.invitations().count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("다른 사람에게 초대받으면")
    class AnotherInviter {

        @Test
        @DisplayName("새로 초대한 사람에게 보상을 돌린다")
        void newInviterRewarded() {
            Inventory inventory = bag();
            accept(inventory, invitedBy(HOST, SHARED_MAP, 1));

            assertThat(accept(inventory, invitedBy(OTHER_HOST, OTHER_MAP, 3)).inviterToReward()).contains(OTHER_HOST);
            assertThat(inventory.invitations().added()).hasSize(2);
        }

        @Test
        @DisplayName("이미 가진 보상 아이템은 다시 생기지 않는다")
        void noDuplicateItem() {
            Inventory inventory = bag();
            accept(inventory, invitedBy(HOST, SHARED_MAP, 1));

            assertThat(accept(inventory, invitedBy(OTHER_HOST, OTHER_MAP, 3)).change().granted()).isEmpty();
        }
    }

    @Nested
    @DisplayName("보상이 없는 합류")
    class NoReward {

        @Test
        @DisplayName("자기 초대로 합류하면 아무것도 받지 않는다")
        void selfInvitation() {
            Inventory inventory = bag();

            assertThat(accept(inventory, invitedBy(ME, SHARED_MAP, 1)).change().changed()).isFalse();
            assertThat(inventory.owns("invite:guest-ticket")).isFalse();
        }

        @Test
        @DisplayName("다시 가입하면 초대 보상이 없다")
        void rejoin() {
            Inventory inventory = bag();

            assertThat(accept(inventory, new Invitation(HOST, SHARED_MAP, true, at(1))).inviterToReward()).isEmpty();
            assertThat(inventory.invitations().count()).isZero();
        }

        @Test
        @DisplayName("초대 없이 합류하면 초대 보상이 없다")
        void withoutInviter() {
            Inventory inventory = bag();

            assertThat(accept(inventory, invitedBy(null, SHARED_MAP, 1)).inviterToReward()).isEmpty();
            assertThat(inventory.owns("invite:guest-ticket")).isFalse();
        }
    }

    @Nested
    @DisplayName("가방을 다시 계산해도")
    class Recalculation {

        @Test
        @DisplayName("초대 보상 아이템은 남는다")
        void keepsItem() {
            Inventory inventory = bag();
            accept(inventory, invitedBy(HOST, SHARED_MAP, 1));

            assertThat(inventory.rebuildBase(Set.of(SHARED_MAP), at(5)).owns("invite:guest-ticket")).isTrue();
        }

        @Test
        @DisplayName("초대받은 기록은 남는다")
        void keepsRecord() {
            Inventory inventory = bag();
            accept(inventory, invitedBy(HOST, SHARED_MAP, 1));

            assertThat(inventory.rebuildBase(Set.of(SHARED_MAP), at(5)).invitations().rewardedBy(HOST)).isTrue();
        }
    }
}
