package com.kobi.territory.wardrobe.domain.inventory;

import static com.kobi.territory.wardrobe.domain.Fixtures.BEANIE;
import static com.kobi.territory.wardrobe.domain.Fixtures.BUSAN;
import static com.kobi.territory.wardrobe.domain.Fixtures.GURYE;
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

import com.kobi.territory.wardrobe.domain.item.GrantKind;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 3단계 QA P2-1(가방 재계산) · P3-R2-2(근거 없는 방문형 아이템 정리) · P3-1(나중 합류자 안전망). */
@DisplayName("가방 다시 계산")
class InventoryReplayTest {

    static final Set<String> BOTH_MAPS = Set.of(PERSONAL_MAP, SHARED_MAP);

    /**
     * 이벤트로 쌓은 가방: 개인 지도 종로(취소 뒤 2회차로 다시 칠함)·부산(이슈 아이템 포함), 공유 지도 구례, 지리산 테마 배경.
     */
    static Inventory accumulated() {
        Inventory inventory = bag();
        inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).at(1).giving(LANTERN));
        inventory.applyCheckIn(checkIn(PERSONAL_MAP, BUSAN).at(2).giving(PARASOL, HANBOK));
        inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(3));
        inventory.applyCheckIn(checkIn(PERSONAL_MAP, JONGNO).round(2).at(4).giving(LANTERN));
        inventory.applyCheckIn(checkIn(SHARED_MAP, GURYE).at(5).giving(BEANIE));
        inventory.grantRewards(List.of(themeBackground("jiri")), at(6));
        return inventory;
    }

    /** 지금 지도에 남아 있는 방문 — 다른 멤버의 방문도 섞여 온다. */
    static final List<ReplayVisit> HISTORY = List.of(
        new ReplayVisit(ME, checkIn(PERSONAL_MAP, BUSAN).at(2).giving(PARASOL, HANBOK)),
        new ReplayVisit(ME, checkIn(PERSONAL_MAP, JONGNO).round(2).at(4).giving(LANTERN)),
        new ReplayVisit(ME, checkIn(SHARED_MAP, GURYE).at(5).giving(BEANIE)),
        new ReplayVisit(HOST, checkIn(SHARED_MAP, JONGNO).at(7).giving(LANTERN)));

    static Inventory replayBothMaps(Inventory inventory, int minute) {
        return InventoryReplay.replay(inventory, BOTH_MAPS, HISTORY, List.of(themeBackground("jiri")), at(minute));
    }

    @Nested
    @DisplayName("지금 멤버인 지도의 방문을 다시 재생하면")
    class Replay {

        @Test
        @DisplayName("그동안 체크인 하나하나로 쌓아 온 가방과 같아진다")
        void sameAsAccumulated() {
            Inventory accumulated = accumulated();
            Inventory replayed = replayBothMaps(accumulated, 10);

            assertThat(replayed.ownedItems().itemIds()).isEqualTo(accumulated.ownedItems().itemIds())
                .containsExactlyInAnyOrder("region:KR-11010", "region:KR-21090", "event:hanbok", "region:KR-36330", "set:jiri");
            assertThat(replayed.ownedItems().all()).containsExactlyInAnyOrderElementsOf(accumulated.ownedItems().all());
        }

        @Test
        @DisplayName("두 번 돌려도 결과가 같다")
        void idempotent() {
            Inventory once = replayBothMaps(accumulated(), 10);
            Inventory twice = replayBothMaps(once, 11);

            assertThat(twice.ownedItems().all()).containsExactlyInAnyOrderElementsOf(once.ownedItems().all());
        }

        @Test
        @DisplayName("다시 칠한 방문은 마지막 회차로 남는다")
        void keepsLatestRound() {
            Inventory replayed = replayBothMaps(accumulated(), 10);

            assertThat(replayed.visitTraces().find(JONGNO, PERSONAL_MAP).orElseThrow().generation()).isEqualTo(2);
        }

        @Test
        @DisplayName("다른 멤버의 방문은 내 가방에 넣지 않는다")
        void ignoresOtherMembers() {
            Inventory replayed = replayBothMaps(accumulated(), 10);

            assertThat(replayed.visitTraces().find(JONGNO, SHARED_MAP)).isEmpty();
        }
    }

    @Nested
    @DisplayName("가방이 망가져 있을 때")
    class Repair {

        /** 보유 아이템 일부와 방문 흔적이 사라진 가방(부산 장식만 즐겨찾기한 채 남음). */
        static Inventory damaged() {
            Inventory accumulated = accumulated();
            accumulated.markFavorite("region:KR-21090", true, at(8));
            OwnedItem parasol = accumulated.find("region:KR-21090").orElseThrow();
            return Inventory.restore(ME, OwnedItems.of(List.of(parasol)), VisitTraces.empty(), at(8));
        }

        @Test
        @DisplayName("빠진 아이템을 되찾는다")
        void restoresMissingItems() {
            Inventory repaired = replayBothMaps(damaged(), 10);

            assertThat(repaired.ownedItems().itemIds()).isEqualTo(accumulated().ownedItems().itemIds());
        }

        @Test
        @DisplayName("남아 있던 아이템의 즐겨찾기는 그대로 둔다")
        void keepsFavorite() {
            assertThat(replayBothMaps(damaged(), 10).find("region:KR-21090").orElseThrow().favorite()).isTrue();
        }

        @Test
        @DisplayName("남아 있던 아이템의 처음 얻은 시각은 그대로 둔다")
        void keepsAcquiredAt() {
            assertThat(replayBothMaps(damaged(), 10).find("region:KR-21090").orElseThrow().acquiredAt()).isEqualTo(at(2));
        }

        @Test
        @DisplayName("이슈 아이템의 근거 방문도 되살린다")
        void restoresBasis() {
            assertThat(replayBothMaps(damaged(), 10).find("event:hanbok").orElseThrow().basis())
                .containsExactly(new VisitKey(BUSAN, PERSONAL_MAP));
        }
    }

    @Nested
    @DisplayName("근거 방문 없이 들어간 아이템이 있을 때")
    class Orphans {

        static Inventory orphaned() {
            return Inventory.restore(ME, OwnedItems.of(List.of(
                OwnedItem.restore("region:KR-26010", GrantKind.REGION_VISIT, T0, false, Set.of()),
                OwnedItem.restore("event:pin", GrantKind.PROVINCE_CHECK_IN, T0, false, Set.of()),
                OwnedItem.restore("set:han", GrantKind.THEME_COMPLETE, T0, true, Set.of()),
                OwnedItem.restore("event:gift", GrantKind.MANUAL, T0, false, Set.of()))), VisitTraces.empty(), T0);
        }

        static Inventory repaired() {
            return InventoryReplay.replay(orphaned(), Set.of(PERSONAL_MAP), List.of(), List.of(), at(10));
        }

        @Test
        @DisplayName("방문으로 받는 특산물·이슈 아이템은 정리한다")
        void dropsVisitItems() {
            assertThat(repaired().owns("region:KR-26010")).isFalse();
            assertThat(repaired().owns("event:pin")).isFalse();
        }

        @Test
        @DisplayName("테마 보상과 직접 받은 아이템은 남기고 즐겨찾기도 지킨다")
        void keepsRewardsAndGifts() {
            assertThat(repaired().ownedItems().itemIds()).containsExactlyInAnyOrder("set:han", "event:gift");
            assertThat(repaired().find("set:han").orElseThrow().favorite()).isTrue();
        }
    }

    @Nested
    @DisplayName("공유 지도에서 탈퇴했을 때")
    class LeftMap {

        static Inventory replayPersonalOnly() {
            return InventoryReplay.replay(accumulated(), Set.of(PERSONAL_MAP), HISTORY.subList(0, 2), List.of(), at(10));
        }

        @Test
        @DisplayName("탈퇴한 지도에서 얻은 아이템은 남는다")
        void keepsItemsFromLeftMap() {
            assertThat(replayPersonalOnly().owns("region:KR-36330")).isTrue();
        }

        @Test
        @DisplayName("테마 보상은 남는다")
        void keepsThemeReward() {
            assertThat(replayPersonalOnly().owns("set:jiri")).isTrue();
        }

        @Test
        @DisplayName("탈퇴한 지도의 방문 흔적도 살아 있는 채로 남는다")
        void keepsTraceOfLeftMap() {
            assertThat(replayPersonalOnly().visitTraces().find(GURYE, SHARED_MAP).orElseThrow().active()).isTrue();
        }
    }

    @Nested
    @DisplayName("이미 완성된 테마가 있는 지도에 나중에 합류했을 때")
    class LateJoiner {

        @Test
        @DisplayName("다시 계산하면 그 테마 보상을 받는다")
        void receivesThemeReward() {
            Inventory replayed = InventoryReplay.replay(bag(), Set.of(SHARED_MAP), List.of(), List.of(themeBackground("jiri")), at(10));

            assertThat(replayed.find("set:jiri").orElseThrow().source()).isEqualTo(ItemSource.SET_REWARD);
        }
    }
}
