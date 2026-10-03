package com.kobi.territory.wardrobe.domain.inventory;

import static com.kobi.territory.wardrobe.domain.Fixtures.ACCOUNT;
import static com.kobi.territory.wardrobe.domain.Fixtures.JONGNO;
import static com.kobi.territory.wardrobe.domain.Fixtures.LANTERN;
import static com.kobi.territory.wardrobe.domain.Fixtures.PERSONAL_MAP;
import static com.kobi.territory.wardrobe.domain.Fixtures.T0;
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
 * 업적 보상(8단계): 시·도 정복 대표 장식(PROVINCE_COMPLETE)과 연속 탐험 마일스톤 한정 아이템(STREAK_MILESTONE). 진행이 알린 업적마다
 * 한 번 — 회수 없는 이벤트 아이템이고, 재계산은 진행 기록의 업적으로 빠진 것을 채운다.
 */
@DisplayName("업적 보상")
class AchievementRewardTest {

    private static final ItemSpec 서울_기념비 = new ItemSpec("conquest:KR-11", ItemSlot.PROP, Rarity.LEGEND, GrantKind.PROVINCE_COMPLETE);
    private static final ItemSpec 석달_키링 = new ItemSpec("streak:3", ItemSlot.BADGE, Rarity.RARE, GrantKind.STREAK_MILESTONE);

    @Nested
    @DisplayName("업적을 이루면")
    class Achieved {

        @Test
        @DisplayName("시·도 정복 장식과 마일스톤 아이템은 거둬 가지 않는 이벤트 아이템으로 들어온다")
        void eventSource() {
            Inventory inventory = bag();

            inventory.grantRewards(List.of(서울_기념비, 석달_키링), at(1));

            assertThat(inventory.find("conquest:KR-11").orElseThrow().source()).isEqualTo(ItemSource.EVENT);
            assertThat(inventory.find("streak:3").orElseThrow().source()).isEqualTo(ItemSource.EVENT);
        }

        @Test
        @DisplayName("같은 업적 소식이 다시 와도 한 번만 들어온다")
        void once() {
            Inventory inventory = bag();
            inventory.grantRewards(List.of(서울_기념비), at(1));

            assertThat(inventory.grantRewards(List.of(서울_기념비), at(2)).granted()).isEmpty();
        }

        @Test
        @DisplayName("체크인을 모두 취소해도 업적 아이템은 남는다")
        void notRevokedByCancel() {
            Inventory inventory = bag();
            inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).at(1).giving(LANTERN));
            inventory.grantRewards(List.of(서울_기념비), at(2));

            inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(3));

            assertThat(inventory.owns("conquest:KR-11")).isTrue();
            assertThat(inventory.owns(LANTERN.itemId())).isFalse();
        }
    }

    @Nested
    @DisplayName("가방을 다시 계산하면")
    class Replay {

        @Test
        @DisplayName("진행 기록의 업적 보상 중 빠진 것을 채운다")
        void recovers() {
            Inventory replayed = InventoryReplay.replay(bag(), Set.of(PERSONAL_MAP), List.of(), List.of(), List.of(서울_기념비, 석달_키링),
                at(10));

            assertThat(replayed.owns("conquest:KR-11")).isTrue();
            assertThat(replayed.owns("streak:3")).isTrue();
        }

        @Test
        @DisplayName("이미 있던 업적 아이템은 처음 얻은 시각을 지킨다")
        void keepsAcquiredAt() {
            Inventory inventory = bag();
            inventory.grantRewards(List.of(서울_기념비), at(1));

            Inventory replayed = InventoryReplay.replay(inventory, Set.of(PERSONAL_MAP), List.of(), List.of(), List.of(서울_기념비), at(10));

            assertThat(replayed.find("conquest:KR-11").orElseThrow().acquiredAt()).isEqualTo(at(1));
        }
    }

    @Test
    @DisplayName("계정으로 합치면 익명 탐험가의 업적 아이템도 계정 가방으로 옮긴다")
    void movedOnMerge() {
        Inventory anonymous = bag();
        anonymous.grantRewards(List.of(서울_기념비, 석달_키링), at(1));
        Inventory account = Inventory.empty(ACCOUNT, T0);

        account.absorbMerged(anonymous, at(5));

        assertThat(account.owns("conquest:KR-11")).isTrue();
        assertThat(account.owns("streak:3")).isTrue();
    }
}
