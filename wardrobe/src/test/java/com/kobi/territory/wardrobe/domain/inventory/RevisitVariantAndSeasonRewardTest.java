package com.kobi.territory.wardrobe.domain.inventory;

import static com.kobi.territory.wardrobe.domain.Fixtures.ACCOUNT;
import static com.kobi.territory.wardrobe.domain.Fixtures.BUSAN;
import static com.kobi.territory.wardrobe.domain.Fixtures.JONGNO;
import static com.kobi.territory.wardrobe.domain.Fixtures.LANTERN;
import static com.kobi.territory.wardrobe.domain.Fixtures.PERSONAL_MAP;
import static com.kobi.territory.wardrobe.domain.Fixtures.at;
import static com.kobi.territory.wardrobe.domain.Fixtures.bag;
import static com.kobi.territory.wardrobe.domain.Fixtures.checkIn;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.wardrobe.domain.item.GrantKind;
import com.kobi.territory.wardrobe.domain.item.ItemSlot;
import com.kobi.territory.wardrobe.domain.item.ItemSpec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 9단계 꾸미기 보상: 재방문 도장을 받은 지역의 특산물은 2회차 색 변형으로(새 아이템이 아니라 보유 아이템의 변형), 계절 회차를 완성하면 그 회차
 * 배경(거둬 가지 않는 테마 보상).
 */
@DisplayName("재방문 색 변형·계절 배경")
class RevisitVariantAndSeasonRewardTest {

    private static final ItemSpec 가을배경 = new ItemSpec("season:autumn-2026", ItemSlot.BG, Rarity.LEGEND, GrantKind.SEASON_COMPLETE);

    @Nested
    @DisplayName("재방문 도장을 받으면")
    class Revisit {

        @Test
        @DisplayName("그 지역 특산물이 2회차 색 변형이 되고 다른 지역은 그대로다")
        void variant() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).giving(LANTERN));

            assertThat(inventory.markRevisited(JONGNO, at(10))).isTrue();

            assertThat(inventory.variantOf(JONGNO)).isEqualTo(RevisitMarks.REVISIT_VARIANT);
            assertThat(inventory.variantOf(BUSAN)).isEqualTo(RevisitMarks.BASE_VARIANT);
            assertThat(inventory.variantOf(null)).isEqualTo(RevisitMarks.BASE_VARIANT);
        }

        @Test
        @DisplayName("같은 지역 도장이 또 와도 표시는 하나다")
        void once() {
            Inventory inventory = bag();
            inventory.markRevisited(JONGNO, at(10));

            assertThat(inventory.markRevisited(JONGNO, at(20))).isFalse();
            assertThat(inventory.revisitMarks().added()).hasSize(1);
        }

        @Test
        @DisplayName("특산물을 거뒀다가 다시 얻어도 변형은 그대로다")
        void survivesRevoke() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).giving(LANTERN));
            inventory.markRevisited(JONGNO, at(10));
            inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(11));

            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).round(2).at(12).giving(LANTERN));

            assertThat(inventory.owns(LANTERN.itemId())).isTrue();
            assertThat(inventory.variantOf(JONGNO)).isEqualTo(RevisitMarks.REVISIT_VARIANT);
        }

        @Test
        @DisplayName("계정으로 합치면 익명의 변형 표시도 옮긴다")
        void merged() {
            Inventory anonymous = bag();
            anonymous.markRevisited(JONGNO, at(10));
            Inventory account = Inventory.empty(ACCOUNT, at(0));

            account.absorbMerged(anonymous, at(20));

            assertThat(account.variantOf(JONGNO)).isEqualTo(RevisitMarks.REVISIT_VARIANT);
        }

        @Test
        @DisplayName("다시 계산할 때 탐험에 남은 도장 지역을 빠짐없이 표시한다")
        void replayed() {
            Inventory rebuilt = InventoryReplay.replay(bag(), Set.of(PERSONAL_MAP), List.of(), List.of(), List.of(),
                List.of(new RevisitMark(JONGNO, at(10))), at(30));

            assertThat(rebuilt.variantOf(JONGNO)).isEqualTo(RevisitMarks.REVISIT_VARIANT);
        }
    }

    @Nested
    @DisplayName("계절 회차를 완성하면")
    class Season {

        @Test
        @DisplayName("회차 배경이 거둬 가지 않는 테마 보상으로 들어오고 같은 소식이 다시 와도 한 번이다")
        void background() {
            Inventory inventory = bag();

            inventory.grantRewards(List.of(가을배경), at(1));

            assertThat(inventory.find("season:autumn-2026").orElseThrow().source()).isEqualTo(ItemSource.SET_REWARD);
            assertThat(inventory.grantRewards(List.of(가을배경), at(2)).granted()).isEmpty();
        }

        @Test
        @DisplayName("계정으로 합치면 익명이 받은 계절 배경을 옮긴다 — 닫힌 회차는 다시 계산되지 않아서")
        void mergedBecauseClosedRoundsAreFinal() {
            Inventory anonymous = bag();
            anonymous.grantRewards(List.of(가을배경), at(1));
            Inventory account = Inventory.empty(ACCOUNT, at(0));

            account.absorbMerged(anonymous, at(5));

            assertThat(account.owns("season:autumn-2026")).isTrue();
        }
    }
}
