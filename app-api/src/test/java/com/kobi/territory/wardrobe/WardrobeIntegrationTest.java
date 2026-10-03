package com.kobi.territory.wardrobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.kobi.territory.catalog.application.ItemCatalogService;
import com.kobi.territory.catalog.application.RegisterItemCommand;
import com.kobi.territory.catalog.domain.item.GrantRule;
import com.kobi.territory.catalog.domain.item.ItemSlot;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.exploration.application.CheckInCommand;
import com.kobi.territory.exploration.application.CheckInService;
import com.kobi.territory.exploration.application.ExplorerService;
import com.kobi.territory.exploration.application.MapService;
import com.kobi.territory.exploration.domain.map.ExpeditionMap;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import com.kobi.territory.wardrobe.application.InventoryRecalculateService;
import com.kobi.territory.wardrobe.application.InventoryService;
import com.kobi.territory.progression.api.event.SetCompleted;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D2: 체크인 → RegionVisited → Inventory(지역 아이템) → ItemGranted → Scene(자동 착용), 취소 → 회수·벗김,
 * 탐험가 단위 회수(다른 지도 활성), 테마 완성 → 멤버 전원 세트 배경, 합류 → 이미 완성된 세트 배경, 이슈 아이템(회수 없음),
 * 옛 세대 이벤트 무시. 회귀 출처: 리더 결정 Q2(근거 방문 회수)·Q-R2-1(소급 없음), QA P3-1(완성 직후 합류자 보상)·P3-17(탈퇴는 회수 아님).
 */
@IntegrationTest
@DisplayName("가방에 아이템이 들고 나기")
class WardrobeIntegrationTest {

    static final Duration WAIT = Duration.ofSeconds(20);
    /** 지리산 둘레: 남원(부채 HAND)·구례(비니 HAT RARE)·하동(삿갓 HAT RARE)·산청(배낭 BAG)·함양(나무 PROP). */
    static final List<String> JIRI = List.of("KR-35050", "KR-36330", "KR-38360", "KR-38370", "KR-38380");

    @Autowired ExplorerService explorers;
    @Autowired CheckInService checkIns;
    @Autowired MapService maps;
    @Autowired InventoryService inventories;
    @Autowired InventoryRecalculateService recalculate;
    @Autowired ItemCatalogService itemCatalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired TransactionTemplate transaction;

    private String register() {
        return explorers.registerAnonymous().explorer().id().value();
    }

    private void checkIn(String explorerId, String mapId, String code) {
        checkIns.checkIn(new CheckInCommand(ExplorerId.of(explorerId), mapId, RegionCode.of(code), LocalDate.now(clock),
            null, null));
    }

    private void cancel(String explorerId, String mapId, String code) {
        checkIns.cancel(ExplorerId.of(explorerId), mapId, RegionCode.of(code));
    }

    /** 지역·세트 아이템(같은 DB 를 쓰는 다른 테스트가 만든 이슈 아이템 event:* 과 4단계 초대 보상 invite:* 은 빼고 본다). */
    private List<String> owned(String explorerId) {
        return jdbc.queryForList("SELECT item_id FROM owned_item WHERE explorer_id = ? AND item_id NOT LIKE 'event:%' AND item_id NOT LIKE 'invite:%' "
            + "ORDER BY item_id", String.class, explorerId);
    }

    /** 재계산 비교용: 보유(출처·처음 얻은 시각·즐겨찾기)·근거·흔적. */
    private Map<String, Object> inventorySnapshot(String explorerId) {
        return Map.of(
            "items", jdbc.queryForList("SELECT item_id, source, acquired_at, favorite FROM owned_item WHERE explorer_id = ? "
                + "ORDER BY item_id", explorerId),
            "basis", jdbc.queryForList("SELECT item_id, region_code, map_id FROM owned_item_basis WHERE explorer_id = ? "
                + "ORDER BY item_id, region_code, map_id", explorerId),
            "visits", jdbc.queryForList("SELECT region_code, map_id, generation, active FROM inventory_visit WHERE explorer_id = ? "
                + "ORDER BY region_code, map_id", explorerId));
    }

    private List<String> eventItems(String explorerId) {
        return jdbc.queryForList("SELECT item_id FROM owned_item WHERE explorer_id = ? AND item_id LIKE 'event:%'",
            String.class, explorerId);
    }

    private Map<String, Object> scene(String explorerId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM scene WHERE explorer_id = ?", explorerId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private void awaitOwned(String explorerId, String... itemIds) {
        await().atMost(WAIT).untilAsserted(() -> assertThat(owned(explorerId)).containsExactlyInAnyOrder(itemIds));
    }

    private void awaitSlot(String explorerId, String column, String itemId) {
        await().atMost(WAIT).untilAsserted(() -> assertThat(scene(explorerId).get(column)).isEqualTo(itemId));
    }

    /** 기간 이슈 아이템(하루짜리)을 만들고 그날로 시계를 옮긴다. 테스트마다 다른 날을 쓴다(같은 DB의 다른 이슈 아이템과 겹치지 않게). */
    private String 기간_이슈_아이템(int dayOffset) {
        String itemId = "event:it-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate eventDay = LocalDate.now(clock).plusYears(3).plusDays(dayOffset);
        itemCatalog.register(new RegisterItemCommand(itemId, "추석 송편 바구니", "🧺", ItemSlot.PET, Rarity.RARE, null,
            "basket", "#e9c46a", "#8d5524", GrantRule.Type.PERIOD_CHECK_IN, null, eventDay, eventDay));
        clock.set(eventDay.atTime(12, 0).atZone(clock.getZone()).toInstant());
        return itemId;
    }

    @Nested
    @DisplayName("체크인으로 받은 지역 아이템")
    class RegionItems {

        @Test
        @DisplayName("칠하면 그 지역 아이템을 받아 빈 슬롯에 자동으로 입는다")
        void grantedAndWorn() {
            String me = register();
            checkIn(me, null, "KR-11010");
            awaitOwned(me, "region:KR-11010");
            awaitSlot(me, "SLOT_HAND", "region:KR-11010");
            assertThat(jdbc.queryForObject("SELECT source FROM owned_item WHERE explorer_id = ?", String.class, me))
                .isEqualTo("REGION");
        }

        @Test
        @DisplayName("취소하면 가방에서 회수하고 벗긴다")
        void cancelRevokesAndUnequips() {
            String me = register();
            checkIn(me, null, "KR-11010");
            awaitSlot(me, "SLOT_HAND", "region:KR-11010");
            cancel(me, null, "KR-11010");
            awaitOwned(me);
            awaitSlot(me, "SLOT_HAND", null);
        }

        @Test
        @DisplayName("입고 벗을 때마다 장면이 바뀌었다는 소식이 나간다")
        void sceneChangedNews() {
            String me = register();
            checkIn(me, null, "KR-11010");
            awaitSlot(me, "SLOT_HAND", "region:KR-11010");
            cancel(me, null, "KR-11010");
            awaitSlot(me, "SLOT_HAND", null);
            await().atMost(WAIT).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%SceneChanged'", Integer.class, me))
                .isEqualTo(2));
        }

        @Test
        @DisplayName("다른 지도에 같은 지역 방문이 남아 있으면 한쪽을 취소해도 회수하지 않는다")
        void keptWhileOtherMapVisitRemains() {
            String me = register();
            ExpeditionMap shared = maps.create(ExplorerId.of(me), "같이 가요", "KR");
            checkIn(me, null, "KR-11020");
            checkIn(me, shared.id().value(), "KR-11020");
            awaitOwned(me, "region:KR-11020");
            cancel(me, null, "KR-11020");
            await().pollDelay(Duration.ofMillis(800)).atMost(WAIT)
                .untilAsserted(() -> assertThat(owned(me)).containsExactly("region:KR-11020"));
        }

        @Test
        @DisplayName("모든 지도에서 그 지역 방문을 취소하면 회수한다")
        void revokedWhenAllVisitsCancelled() {
            String me = register();
            ExpeditionMap shared = maps.create(ExplorerId.of(me), "같이 가요", "KR");
            checkIn(me, null, "KR-11020");
            checkIn(me, shared.id().value(), "KR-11020");
            awaitOwned(me, "region:KR-11020");
            cancel(me, null, "KR-11020");
            cancel(me, shared.id().value(), "KR-11020");
            awaitOwned(me);
        }

        @Test
        @DisplayName("공유 지도를 떠나도 그 지도에서 얻은 아이템은 남고 다시 계산해도 남는다")
        void leavingDoesNotRevoke() {
            String owner = register();
            String member = register();
            ExpeditionMap shared = maps.create(ExplorerId.of(owner), "잠깐 같이", "KR");
            maps.join(ExplorerId.of(member), shared.inviteCode().value());
            checkIn(member, shared.id().value(), "KR-11060");
            awaitOwned(member, "region:KR-11060");

            maps.leave(ExplorerId.of(member), shared.id());
            await().atMost(WAIT).until(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE published_at IS NULL AND aggregate_id IN (?, ?)", Integer.class,
                shared.id().value(), member) == 0);
            assertThat(owned(member)).containsExactly("region:KR-11060");
            recalculate.recalculate(ExplorerId.of(member));
            assertThat(owned(member)).containsExactly("region:KR-11060");
        }

        @Test
        @DisplayName("다시 칠한 뒤 늦게 다시 온 예전 회차의 취소는 무시한다")
        void staleGenerationCancelIgnored() {
            String me = register();
            String mapId = jdbc.queryForObject("SELECT id FROM expedition_map WHERE owner_id = ?", String.class, me);
            checkIn(me, null, "KR-11040");
            awaitOwned(me, "region:KR-11040");
            cancel(me, null, "KR-11040");
            awaitOwned(me);
            checkIn(me, null, "KR-11040");
            awaitOwned(me, "region:KR-11040");
            Integer generation = jdbc.queryForObject(
                "SELECT generation FROM inventory_visit WHERE explorer_id = ? AND region_code = 'KR-11040'", Integer.class, me);
            assertThat(generation).isGreaterThan(1);

            inventories.onVisitCancelled(new VisitCancelled(me, mapId, "KR-11040", Rarity.COMMON, "KR-11", true, 0, false,
                clock.instant(), 1));

            assertThat(owned(me)).containsExactly("region:KR-11040");
        }
    }

    @Nested
    @DisplayName("자동 착용")
    class AutoEquip {

        @Test
        @DisplayName("빈 슬롯이면 입고, 더 희귀한 아이템이 들어오면 바꿔 입는다")
        void emptyOrRarer() {
            String me = register();
            checkIn(me, null, "KR-37040");   // 안동 하회탈(HAT 일반)
            awaitSlot(me, "SLOT_HAT", "region:KR-37040");
            checkIn(me, null, "KR-36330");   // 구례 비니(HAT 희귀)
            awaitSlot(me, "SLOT_HAT", "region:KR-36330");
        }

        @Test
        @DisplayName("같거나 덜 희귀한 아이템이 들어오면 입던 것을 그대로 입는다")
        void notLessRare() {
            String me = register();
            checkIn(me, null, "KR-36330");   // 구례 비니(HAT 희귀)
            awaitSlot(me, "SLOT_HAT", "region:KR-36330");
            checkIn(me, null, "KR-36360");   // 보성 녹차 삿갓(HAT 희귀) — 같은 희귀도
            checkIn(me, null, "KR-37040");   // 안동 하회탈(HAT 일반) — 덜 희귀
            checkIn(me, null, "KR-38070");   // 가야 왕관(HAT 일반) — 덜 희귀
            awaitOwned(me, "region:KR-37040", "region:KR-36330", "region:KR-36360", "region:KR-38070");
            await().pollDelay(Duration.ofMillis(800)).atMost(WAIT)
                .untilAsserted(() -> assertThat(scene(me).get("SLOT_HAT")).isEqualTo("region:KR-36330"));
        }
    }

    @Nested
    @DisplayName("함께 테마를 완성하면")
    class ThemeRewards {

        record Crew(String owner, String friend, ExpeditionMap shared) {}

        /** 지도장이 지리산 네 곳, 친구가 마지막 한 곳을 칠해 완성한 지도. */
        private Crew 지리산을_완성한_지도() {
            String owner = register();
            String friend = register();
            ExpeditionMap shared = maps.create(ExplorerId.of(owner), "지리산 원정대", "KR");
            String mapId = shared.id().value();
            maps.join(ExplorerId.of(friend), shared.inviteCode().value());
            JIRI.subList(0, 4).forEach(code -> checkIn(owner, mapId, code));
            checkIn(friend, mapId, JIRI.get(4));
            await().atMost(WAIT).untilAsserted(() -> {
                assertThat(owned(owner)).contains("set:jiri");
                assertThat(owned(friend)).containsExactlyInAnyOrder("region:KR-38380", "set:jiri");
            });
            return new Crew(owner, friend, shared);
        }

        @Test
        @DisplayName("완성 순간의 멤버 모두가 테마 보상 배경을 받는다")
        void allMembersGetBackground() {
            Crew crew = 지리산을_완성한_지도();
            assertThat(jdbc.queryForObject("SELECT source FROM owned_item WHERE explorer_id = ? AND item_id = 'set:jiri'",
                String.class, crew.friend())).isEqualTo("SET_REWARD");
        }

        @Test
        @DisplayName("전설 테마 배경은 빈 배경 슬롯에 자동으로 입는다")
        void legendBackgroundAutoWorn() {
            Crew crew = 지리산을_완성한_지도();
            awaitSlot(crew.friend(), "SLOT_BG", "set:jiri");
        }

        @Test
        @DisplayName("완성 뒤에 합류한 멤버도 테마 배경을 받는다")
        void lateMemberGetsBackground() {
            Crew crew = 지리산을_완성한_지도();
            String late = register();
            maps.join(ExplorerId.of(late), crew.shared().inviteCode().value());
            awaitOwned(late, "set:jiri");
        }

        @Test
        @DisplayName("테마 보상은 칠한 곳을 취소해도 회수하지 않는다")
        void notRevokedOnCancel() {
            Crew crew = 지리산을_완성한_지도();
            cancel(crew.friend(), crew.shared().id().value(), JIRI.get(4));
            awaitOwned(crew.friend(), "set:jiri");
        }

        @Test
        @DisplayName("완성 소식을 처리할 때 수령자 목록에 없는 지금 멤버에게도 각자 따로 배경을 준다")
        void currentMemberMissingFromRecipients() {
            String owner = register();
            String late = register();
            ExpeditionMap shared = maps.create(ExplorerId.of(owner), "늦은 합류", "KR");
            String mapId = shared.id().value();
            maps.join(ExplorerId.of(late), shared.inviteCode().value());
            await().atMost(WAIT).until(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE published_at IS NULL AND aggregate_id = ?", Integer.class, mapId) == 0);
            assertThat(owned(late)).isEmpty();

            transaction.executeWithoutResult(status ->
                inventories.onSetCompleted(new SetCompleted(mapId, "jiri", owner, clock.instant(), owner, List.of(owner))));

            assertThat(owned(owner)).containsExactly("set:jiri");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%ThemeRewardOwed'",
                Integer.class, late)).isEqualTo(1);
            awaitOwned(late, "set:jiri");
        }
    }

    @Nested
    @DisplayName("체크인 이슈 아이템")
    class EventItems {

        @Test
        @DisplayName("기간 안에 칠하면 이슈 아이템을 받아 입는다")
        void grantedWithinPeriod() {
            Instant original = clock.instant();
            try {
                String itemId = 기간_이슈_아이템(0);
                String me = register();
                checkIn(me, null, "KR-11030");
                awaitOwned(me, "region:KR-11030");
                await().atMost(WAIT).untilAsserted(() -> assertThat(eventItems(me)).containsExactly(itemId));
                awaitSlot(me, "SLOT_PET", itemId);
                assertThat(jdbc.queryForObject("SELECT source FROM owned_item WHERE explorer_id = ? AND item_id = ?", String.class,
                    me, itemId)).isEqualTo("EVENT");
            } finally {
                clock.set(original);
            }
        }

        @Test
        @DisplayName("같은 조건을 만족한 다른 방문이 남아 있으면 한 곳을 취소해도 유지한다")
        void keptWhileAnotherBasisRemains() {
            Instant original = clock.instant();
            try {
                String itemId = 기간_이슈_아이템(10);
                String me = register();
                checkIn(me, null, "KR-11030");
                checkIn(me, null, "KR-11050");
                await().atMost(WAIT).untilAsserted(() -> assertThat(eventItems(me)).containsExactly(itemId));
                cancel(me, null, "KR-11030");
                awaitOwned(me, "region:KR-11050");
                assertThat(eventItems(me)).containsExactly(itemId);
            } finally {
                clock.set(original);
            }
        }

        @Test
        @DisplayName("근거 방문이 모두 취소되면 회수하고 벗긴다 — 칠하고 바로 취소해 얻을 수는 없다")
        void revokedWhenAllBasesCancelled() {
            Instant original = clock.instant();
            try {
                String itemId = 기간_이슈_아이템(20);
                String me = register();
                checkIn(me, null, "KR-11030");
                checkIn(me, null, "KR-11050");
                awaitSlot(me, "SLOT_PET", itemId);
                cancel(me, null, "KR-11030");
                cancel(me, null, "KR-11050");
                awaitOwned(me);
                await().atMost(WAIT).untilAsserted(() -> assertThat(eventItems(me)).isEmpty());
                awaitSlot(me, "SLOT_PET", null);
            } finally {
                clock.set(original);
            }
        }

        @Test
        @DisplayName("정의가 생기기 전의 체크인에는 다시 계산해도 소급해 주지 않는다")
        void noRetroactiveGrant() {
            String me = register();
            checkIn(me, null, "KR-26010"); // 대구 — 이슈 아이템을 만들기 전의 체크인
            awaitOwned(me, "region:KR-26010");
            clock.advance(Duration.ofMinutes(1));
            String itemId = "event:late-" + UUID.randomUUID().toString().substring(0, 8);
            itemCatalog.register(new RegisterItemCommand(itemId, "대구 사과 핀", "🍎", ItemSlot.BADGE, Rarity.RARE, null,
                null, null, null, GrantRule.Type.PROVINCE_CHECK_IN, "KR-26", null, null));
            recalculate.recalculate(ExplorerId.of(me));
            assertThat(eventItems(me)).doesNotContain(itemId);
        }

        @Test
        @DisplayName("정의가 생긴 뒤의 체크인으로 받고, 다시 계산해도 근거는 그 뒤의 방문뿐이다")
        void grantedAfterDefinition() {
            String me = register();
            checkIn(me, null, "KR-26010");
            awaitOwned(me, "region:KR-26010");
            clock.advance(Duration.ofMinutes(1));
            String itemId = "event:late-" + UUID.randomUUID().toString().substring(0, 8);
            itemCatalog.register(new RegisterItemCommand(itemId, "대구 사과 핀", "🍎", ItemSlot.BADGE, Rarity.RARE, null,
                null, null, null, GrantRule.Type.PROVINCE_CHECK_IN, "KR-26", null, null));
            clock.advance(Duration.ofMinutes(1));
            checkIn(me, null, "KR-26020");
            await().atMost(WAIT).untilAsserted(() -> assertThat(eventItems(me)).contains(itemId));
            recalculate.recalculate(ExplorerId.of(me));
            assertThat(eventItems(me)).contains(itemId);
            assertThat(jdbc.queryForList("SELECT region_code FROM owned_item_basis WHERE explorer_id = ? AND item_id = ?",
                String.class, me, itemId)).containsExactly("KR-26020");
        }
    }

    @Nested
    @DisplayName("가방을 다시 계산하면")
    class Recalculation {

        record Damaged(String me, Map<String, Object> healthy) {}

        /** 종로(손)·해운대(장식, 즐겨찾기)를 받은 뒤 아이템·근거·흔적 일부를 지우고 가방에 없는 모자를 입힌다. */
        private Damaged 망가진_가방() {
            String me = register();
            checkIn(me, null, "KR-11010");
            checkIn(me, null, "KR-21090");
            awaitOwned(me, "region:KR-11010", "region:KR-21090");
            awaitSlot(me, "SLOT_HAND", "region:KR-11010");
            jdbc.update("UPDATE owned_item SET favorite = TRUE WHERE explorer_id = ? AND item_id = 'region:KR-21090'", me);
            Map<String, Object> healthy = inventorySnapshot(me);
            jdbc.update("DELETE FROM owned_item_basis WHERE explorer_id = ? AND item_id = 'region:KR-11010'", me);
            jdbc.update("DELETE FROM owned_item WHERE explorer_id = ? AND item_id = 'region:KR-11010'", me);
            jdbc.update("DELETE FROM inventory_visit WHERE explorer_id = ?", me);
            jdbc.update("UPDATE scene SET slot_hat = 'region:KR-36330' WHERE explorer_id = ?", me);
            return new Damaged(me, healthy);
        }

        @Test
        @DisplayName("잃어버린 아이템·근거·흔적을 되살리고 처음 얻은 시각과 즐겨찾기를 지킨다")
        void restoresInventory() {
            Damaged d = 망가진_가방();
            recalculate.recalculate(ExplorerId.of(d.me()));
            assertThat(inventorySnapshot(d.me())).isEqualTo(d.healthy());
        }

        @Test
        @DisplayName("가방에 없는 것을 입고 있으면 벗기고 나머지 착용은 그대로 둔다")
        void unequipsMissingItemsOnly() {
            Damaged d = 망가진_가방();
            recalculate.recalculate(ExplorerId.of(d.me()));
            assertThat(scene(d.me()).get("SLOT_HAT")).isNull();
            assertThat(scene(d.me()).get("SLOT_HAND")).isEqualTo("region:KR-11010");
        }

        @Test
        @DisplayName("두 번 돌려도 같고, 소식이 다 전달된 상태면 바로 돈다")
        void idempotent() {
            Damaged d = 망가진_가방();
            recalculate.recalculate(ExplorerId.of(d.me()));
            recalculate.recalculate(ExplorerId.of(d.me()));
            assertThat(inventorySnapshot(d.me())).isEqualTo(d.healthy());
            assertThat(recalculate.recalculateIfSettled(ExplorerId.of(d.me()))).isTrue();
        }
    }
}
