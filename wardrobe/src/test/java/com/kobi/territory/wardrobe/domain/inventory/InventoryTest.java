package com.kobi.territory.wardrobe.domain.inventory;

import static com.kobi.territory.wardrobe.domain.Fixtures.ACCOUNT;
import static com.kobi.territory.wardrobe.domain.Fixtures.BUSAN;
import static com.kobi.territory.wardrobe.domain.Fixtures.GIFT;
import static com.kobi.territory.wardrobe.domain.Fixtures.GUEST_TICKET;
import static com.kobi.territory.wardrobe.domain.Fixtures.HANBOK;
import static com.kobi.territory.wardrobe.domain.Fixtures.HOST;
import static com.kobi.territory.wardrobe.domain.Fixtures.JONGNO;
import static com.kobi.territory.wardrobe.domain.Fixtures.LANTERN;
import static com.kobi.territory.wardrobe.domain.Fixtures.ME;
import static com.kobi.territory.wardrobe.domain.Fixtures.PARASOL;
import static com.kobi.territory.wardrobe.domain.Fixtures.PERSONAL_MAP;
import static com.kobi.territory.wardrobe.domain.Fixtures.SHARED_MAP;
import static com.kobi.territory.wardrobe.domain.Fixtures.T0;
import static com.kobi.territory.wardrobe.domain.Fixtures.at;
import static com.kobi.territory.wardrobe.domain.Fixtures.bag;
import static com.kobi.territory.wardrobe.domain.Fixtures.checkIn;
import static com.kobi.territory.wardrobe.domain.Fixtures.themeBackground;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.wardrobe.domain.item.GrantKind;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 3단계 D1 · 리더 결정 Q2(즉시 취소로 이슈 아이템 얻기 차단) · Q3(회차를 모르는 예전 소식) · 4단계 QA P3-6(병합). */
@DisplayName("가방")
class InventoryTest {

    @Nested
    @DisplayName("체크인하면")
    class CheckIn {

        @Test
        @DisplayName("그 지역의 특산물을 얻는다")
        void grantsRegionItem() {
            Inventory inventory = bag();
            InventoryChange change = inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).giving(LANTERN));

            assertThat(change.granted()).extracting(OwnedItem::itemId).containsExactly("region:KR-11010");
            assertThat(change.granted().get(0).source()).isEqualTo(ItemSource.REGION);
        }

        @Test
        @DisplayName("얻은 아이템은 그 방문을 근거로 기억한다")
        void remembersBasis() {
            Inventory inventory = bag();
            InventoryChange change = inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).giving(LANTERN));

            assertThat(change.granted().get(0).basis()).containsExactly(new VisitKey(JONGNO, PERSONAL_MAP));
        }

        @Test
        @DisplayName("같은 체크인 소식이 다시 와도 한 번만 들어간다")
        void sameCheckInTwiceGrantsOnce() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).giving(LANTERN));

            assertThat(inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).giving(LANTERN)).changed()).isFalse();
            assertThat(inventory.ownedItems().count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("체크인을 취소하면")
    class Cancel {

        @Test
        @DisplayName("근거 방문이 모두 취소된 지역 특산물과 이슈 아이템을 거둔다 — 체크인 직후 취소로 이슈 아이템을 얻을 수 없다")
        void revokesItemsWithoutBasis() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).giving(LANTERN, HANBOK));

            InventoryChange cancelled = inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(5));

            assertThat(cancelled.revoked()).extracting(OwnedItem::itemId).containsExactlyInAnyOrder("region:KR-11010", "event:hanbok");
            assertThat(inventory.ownedItems().count()).isZero();
        }

        @Test
        @DisplayName("같은 조건을 만족한 다른 방문이 남으면 이슈 아이템은 그 방문을 근거로 남는다")
        void eventItemStaysOnOtherVisit() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).at(1).giving(LANTERN, HANBOK));
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, BUSAN).at(2).giving(PARASOL, HANBOK));

            inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(5));

            assertThat(inventory.owns("region:KR-11010")).isFalse();
            assertThat(inventory.find("event:hanbok").orElseThrow().basis()).containsExactly(new VisitKey(BUSAN, PERSONAL_MAP));
            assertThat(inventory.find("event:hanbok").orElseThrow().source()).isEqualTo(ItemSource.EVENT);
        }

        @Test
        @DisplayName("남은 방문까지 취소하면 이슈 아이템도 거둔다")
        void eventItemGoesWithLastVisit() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).at(1).giving(LANTERN, HANBOK));
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, BUSAN).at(2).giving(PARASOL, HANBOK));
            inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(5));

            inventory.applyCancel(PERSONAL_MAP, BUSAN, 1, at(6));

            assertThat(inventory.owns("event:hanbok")).isFalse();
        }

        @Test
        @DisplayName("다른 지도에 같은 지역 방문이 남아 있으면 거두지 않는다")
        void keepsWhileOtherMapStillVisited() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).giving(LANTERN));
            inventory.applyCheckIn(checkIn(SHARED_MAP, JONGNO).giving(LANTERN));

            assertThat(inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(5)).changed()).isFalse();
            assertThat(inventory.owns("region:KR-11010")).isTrue();
        }

        @Test
        @DisplayName("마지막 지도의 방문까지 취소하면 거둔다")
        void revokesWithLastMap() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).giving(LANTERN));
            inventory.applyCheckIn(checkIn(SHARED_MAP, JONGNO).giving(LANTERN));
            inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(5));

            assertThat(inventory.applyCancel(SHARED_MAP, JONGNO, 1, at(6)).revoked()).hasSize(1);
            assertThat(inventory.owns("region:KR-11010")).isFalse();
        }

        @Test
        @DisplayName("모르는 방문의 취소는 가방을 바꾸지 않는다")
        void unknownCancelChangesNothing() {
            Inventory inventory = bag();

            assertThat(inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(1)).changed()).isFalse();
            assertThat(inventory.visitTraces().changed()).isEmpty();
        }
    }

    @Nested
    @DisplayName("보상을 받으면")
    class Rewards {

        @Test
        @DisplayName("테마 완성 배경은 테마 보상으로 들어온다")
        void themeRewardSource() {
            Inventory inventory = bag();

            assertThat(inventory.grantRewards(List.of(themeBackground("han")), at(2)).granted())
                .extracting(OwnedItem::source).containsExactly(ItemSource.SET_REWARD);
        }

        @Test
        @DisplayName("같은 테마 보상이 다시 와도 한 번만 들어간다")
        void themeRewardOnce() {
            Inventory inventory = bag();
            inventory.grantRewards(List.of(themeBackground("han")), at(2));

            assertThat(inventory.grantRewards(List.of(themeBackground("han")), at(3)).changed()).isFalse();
        }

        @Test
        @DisplayName("체크인을 취소해도 테마 보상은 남는다")
        void themeRewardSurvivesCancel() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).giving(LANTERN));
            inventory.grantRewards(List.of(themeBackground("han")), at(2));

            inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(5));

            assertThat(inventory.owns("set:han")).isTrue();
        }

        @Test
        @DisplayName("운영이 직접 준 아이템은 체크인을 취소해도 남는다")
        void manualGiftSurvivesCancel() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).giving(LANTERN));
            inventory.grantRewards(List.of(GIFT), at(2));

            inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(5));

            assertThat(inventory.find("event:gift").orElseThrow().source()).isEqualTo(ItemSource.EVENT);
        }
    }

    @Nested
    @DisplayName("늦게 도착한 옛 회차 소식은")
    class OldRounds {

        @Test
        @DisplayName("다시 칠한 뒤 늦게 온 이전 회차의 취소가 새 방문을 끄지 않는다")
        void staleCancelIgnored() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).round(1).giving(LANTERN));
            inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(2));
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).round(2).giving(LANTERN));

            assertThat(inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(4)).changed()).isFalse();
            assertThat(inventory.owns("region:KR-11010")).isTrue();
        }

        @Test
        @DisplayName("이미 취소된 회차의 체크인이 다시 와도 아이템을 다시 주지 않는다")
        void cancelledRoundCheckInIgnored() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).round(1).giving(LANTERN));
            inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(2));
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).round(2).giving(LANTERN));
            inventory.applyCancel(PERSONAL_MAP, JONGNO, 2, at(5));

            assertThat(inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).round(1).giving(LANTERN)).changed()).isFalse();
            assertThat(inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).round(2).giving(LANTERN)).changed()).isFalse();
            assertThat(inventory.owns("region:KR-11010")).isFalse();
            assertThat(inventory.visitTraces().find(JONGNO, PERSONAL_MAP).orElseThrow().generation()).isEqualTo(2);
        }

        @Test
        @DisplayName("회차를 모르는 예전 취소는 회차 기록이 있으면 무시한다")
        void legacyCancelIgnoredWhenRoundKnown() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).round(3).giving(LANTERN));

            assertThat(inventory.applyCancel(PERSONAL_MAP, JONGNO, 0, at(5)).changed()).isFalse();
            assertThat(inventory.owns("region:KR-11010")).isTrue();
        }

        @Test
        @DisplayName("회차를 모르는 예전 체크인은 회차 기록이 있으면 무시한다")
        void legacyCheckInIgnoredWhenRoundKnown() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).round(3).giving(LANTERN));
            inventory.applyCancel(PERSONAL_MAP, JONGNO, 3, at(6));

            assertThat(inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).round(0).giving(LANTERN)).changed()).isFalse();
            assertThat(inventory.visitTraces().find(JONGNO, PERSONAL_MAP).orElseThrow().generation()).isEqualTo(3);
        }

        @Test
        @DisplayName("회차 기록이 없으면 회차를 모르는 예전 소식도 그대로 반영한다")
        void legacyEventsAppliedWithoutRounds() {
            Inventory inventory = bag();

            assertThat(inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).round(0).giving(LANTERN)).granted()).hasSize(1);
            assertThat(inventory.applyCancel(PERSONAL_MAP, JONGNO, 0, at(5)).revoked()).hasSize(1);
            assertThat(inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).round(0).giving(LANTERN)).granted()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("즐겨찾기")
    class Favorite {

        @Test
        @DisplayName("가방에 있는 아이템을 즐겨찾기할 수 있다")
        void marksOwnedItem() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).giving(LANTERN));

            assertThat(inventory.markFavorite("region:KR-11010", true, at(3)).favorite()).isTrue();
        }

        @Test
        @DisplayName("방금 얻은 아이템을 바로 즐겨찾기해도 가방에는 새로 얻은 한 개로 남는다")
        void newItemFavoriteRecordedOnce() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).giving(LANTERN));

            inventory.markFavorite("region:KR-11010", true, at(3));

            assertThat(inventory.ownedItems().added()).hasSize(1);
            assertThat(inventory.ownedItems().updated()).isEmpty();
        }

        @Test
        @DisplayName("가방에 없는 아이템은 즐겨찾기할 수 없다")
        void rejectsItemNotOwned() {
            Inventory inventory = bag();

            assertThatThrownBy(() -> inventory.markFavorite("region:KR-26010", true, at(3)))
                .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "ITEM_NOT_OWNED");
        }
    }

    @Nested
    @DisplayName("전에 쌓인 가방에서 바꾸면")
    class Restored {

        Inventory restored() {
            VisitKey jongno = new VisitKey(JONGNO, PERSONAL_MAP);
            OwnedItem lantern = OwnedItem.restore("region:KR-11010", GrantKind.REGION_VISIT, T0, false, Set.of(jongno));
            OwnedItem background = OwnedItem.restore("set:han", GrantKind.THEME_COMPLETE, T0, false, Set.of());
            Inventory inventory = Inventory.restore(ME, OwnedItems.of(List.of(lantern, background)),
                VisitTraces.of(List.of(VisitTrace.restore(JONGNO, PERSONAL_MAP, 1, true))), T0);
            inventory.markFavorite("set:han", true, at(1));
            inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(2));
            return inventory;
        }

        @Test
        @DisplayName("거둔 아이템은 지워지고 새로 생긴 아이템은 없다")
        void revokedItemRemoved() {
            Inventory inventory = restored();

            assertThat(inventory.ownedItems().removed()).containsExactly("region:KR-11010");
            assertThat(inventory.ownedItems().added()).isEmpty();
        }

        @Test
        @DisplayName("즐겨찾기만 바꾼 아이템은 고쳐 적힌다")
        void favoriteUpdated() {
            assertThat(restored().ownedItems().updated()).extracting(OwnedItem::itemId).containsExactly("set:han");
        }

        @Test
        @DisplayName("취소한 방문의 흔적은 꺼진 채로 남는다")
        void traceTurnedOff() {
            assertThat(restored().visitTraces().changed()).extracting(VisitTrace::active).containsExactly(false);
        }

        @Test
        @DisplayName("남은 아이템을 최근 얻은 순으로 보여 준다")
        void newestFirst() {
            assertThat(restored().ownedItems().newestFirst()).extracting(OwnedItem::itemId).containsExactly("set:han");
        }
    }

    @Nested
    @DisplayName("익명 탐험가가 계정으로 합쳐지면")
    class Merge {

        Inventory anonymous() {
            Inventory anonymous = bag();
            anonymous.acceptInvitation(new Invitation(HOST, SHARED_MAP, false, at(1)), List.of(GUEST_TICKET));
            anonymous.grantRewards(List.of(themeBackground("jiri")), at(2));
            anonymous.grantRewards(List.of(GIFT), at(3));
            return anonymous;
        }

        @Test
        @DisplayName("다시 만들 수 없는 초대 보상과 직접 받은 아이템을 계정 가방으로 옮긴다")
        void adoptsUnreplayableItems() {
            Inventory into = Inventory.empty(ACCOUNT, T0);

            InventoryChange change = into.absorbMerged(anonymous(), at(9));

            assertThat(change.granted()).extracting(OwnedItem::itemId).containsExactlyInAnyOrder("invite:guest-ticket", "event:gift");
        }

        @Test
        @DisplayName("옮긴 아이템은 처음 얻은 시각을 그대로 둔다")
        void keepsAcquiredAt() {
            Inventory into = Inventory.empty(ACCOUNT, T0);
            into.absorbMerged(anonymous(), at(9));

            assertThat(into.find("invite:guest-ticket").orElseThrow().acquiredAt()).isEqualTo(at(1));
        }

        @Test
        @DisplayName("테마 보상은 옮기지 않고 다시 계산에 맡긴다")
        void leavesThemeRewardToRecalculation() {
            Inventory into = Inventory.empty(ACCOUNT, T0);
            into.absorbMerged(anonymous(), at(9));

            assertThat(into.owns("set:jiri")).isFalse();
        }

        @Test
        @DisplayName("초대받은 기록도 옮긴다")
        void adoptsInvitations() {
            Inventory into = Inventory.empty(ACCOUNT, T0);
            into.absorbMerged(anonymous(), at(9));

            assertThat(into.invitations().rewardedBy(HOST)).isTrue();
        }

        @Test
        @DisplayName("합치기 소식이 다시 와도 겹쳐 들어가지 않는다")
        void mergeTwiceAddsNothing() {
            Inventory anonymous = anonymous();
            Inventory into = Inventory.empty(ACCOUNT, T0);
            into.absorbMerged(anonymous, at(9));

            assertThat(into.absorbMerged(anonymous, at(10)).granted()).isEmpty();
            assertThat(into.invitations().added()).hasSize(1);
        }
    }
}
