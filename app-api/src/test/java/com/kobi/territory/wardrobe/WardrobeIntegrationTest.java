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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D2: 체크인 → RegionVisited → Inventory(지역 아이템) → ItemGranted → Scene(자동 착용), 취소 → 회수·벗김,
 * 탐험가 단위 회수(다른 지도 활성), 테마 완성 → 멤버 전원 세트 배경, 합류 → 이미 완성된 세트 배경, 이슈 아이템(회수 없음),
 * 옛 세대 이벤트 무시.
 */
@IntegrationTest
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

    /** 지역·세트 아이템(같은 DB 를 쓰는 다른 테스트가 만든 이슈 아이템 event:* 은 빼고 본다). */
    private List<String> owned(String explorerId) {
        return jdbc.queryForList("SELECT item_id FROM owned_item WHERE explorer_id = ? AND item_id NOT LIKE 'event:%' "
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

    @Test
    void 체크인하면_지역_아이템을_얻고_자동_착용_취소하면_회수하고_벗긴다() {
        String me = register();
        checkIn(me, null, "KR-11010");
        awaitOwned(me, "region:KR-11010");
        awaitSlot(me, "SLOT_HAND", "region:KR-11010");
        assertThat(jdbc.queryForObject("SELECT source FROM owned_item WHERE explorer_id = ?", String.class, me))
            .isEqualTo("REGION");

        cancel(me, null, "KR-11010");
        awaitOwned(me);
        awaitSlot(me, "SLOT_HAND", null);
        // SceneChanged 발행(착용·벗김 두 번)
        await().atMost(WAIT).untilAsserted(() -> assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%SceneChanged'", Integer.class, me))
            .isEqualTo(2));
    }

    @Test
    void 자동_착용은_빈_슬롯이거나_더_희귀할_때만() {
        String me = register();
        checkIn(me, null, "KR-37040");   // 안동 하회탈(HAT 일반)
        awaitSlot(me, "SLOT_HAT", "region:KR-37040");
        checkIn(me, null, "KR-36330");   // 구례 비니(HAT 희귀) → 바꿔 입음
        awaitSlot(me, "SLOT_HAT", "region:KR-36330");
        checkIn(me, null, "KR-38070");   // 가야 왕관(HAT 일반) → 그대로
        awaitOwned(me, "region:KR-37040", "region:KR-36330", "region:KR-38070");
        await().pollDelay(Duration.ofMillis(800)).atMost(WAIT)
            .untilAsserted(() -> assertThat(scene(me).get("SLOT_HAT")).isEqualTo("region:KR-36330"));
    }

    @Test
    void 다른_지도에_같은_지역_활성_방문이_남으면_회수하지_않는다() {
        String me = register();
        ExpeditionMap shared = maps.create(ExplorerId.of(me), "같이 가요", "KR");
        checkIn(me, null, "KR-11020");
        checkIn(me, shared.id().value(), "KR-11020");
        awaitOwned(me, "region:KR-11020");

        cancel(me, null, "KR-11020");
        await().pollDelay(Duration.ofMillis(800)).atMost(WAIT)
            .untilAsserted(() -> assertThat(owned(me)).containsExactly("region:KR-11020"));

        cancel(me, shared.id().value(), "KR-11020");
        awaitOwned(me);
    }

    @Test
    void 테마_완성_시점_멤버_전원이_세트_배경을_받고_나중_합류_멤버도_받는다() {
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
        assertThat(jdbc.queryForObject("SELECT source FROM owned_item WHERE explorer_id = ? AND item_id = 'set:jiri'",
            String.class, friend)).isEqualTo("SET_REWARD");
        // 전설 세트 배경은 빈 BG 슬롯에 자동 착용
        awaitSlot(friend, "SLOT_BG", "set:jiri");

        String late = register();
        maps.join(ExplorerId.of(late), shared.inviteCode().value());
        awaitOwned(late, "set:jiri");

        // 세트 보상은 취소해도 회수 없음
        cancel(friend, mapId, JIRI.get(4));
        awaitOwned(friend, "set:jiri");
    }

    @Test
    void 이슈_아이템은_기간_안_체크인으로_받고_근거_방문이_모두_취소되면_회수된다_Q2() {
        // 다른 테스트(같은 DB)의 체크인이 받지 않도록 먼 미래 하루짜리 기간으로 만들고 그날로 시계를 옮긴다
        String itemId = "event:it-" + UUID.randomUUID().toString().substring(0, 8);
        Instant original = clock.instant();
        LocalDate eventDay = LocalDate.now(clock).plusYears(3);
        itemCatalog.register(new RegisterItemCommand(itemId, "추석 송편 바구니", "🧺", ItemSlot.PET, Rarity.RARE, null,
            "basket", "#e9c46a", "#8d5524", GrantRule.Type.PERIOD_CHECK_IN, null, eventDay, eventDay));
        try {
            clock.set(eventDay.atTime(12, 0).atZone(clock.getZone()).toInstant());
            String me = register();
            checkIn(me, null, "KR-11030");
            checkIn(me, null, "KR-11050");
            awaitOwned(me, "region:KR-11030", "region:KR-11050");
            await().atMost(WAIT).untilAsserted(() -> assertThat(eventItems(me)).containsExactly(itemId));
            awaitSlot(me, "SLOT_PET", itemId);
            assertThat(jdbc.queryForObject("SELECT source FROM owned_item WHERE explorer_id = ? AND item_id = ?", String.class,
                me, itemId)).isEqualTo("EVENT");

            // 같은 조건(기간 안 체크인)을 만족한 다른 방문이 남아 있으면 유지
            cancel(me, null, "KR-11030");
            awaitOwned(me, "region:KR-11050");
            assertThat(eventItems(me)).containsExactly(itemId);
            // 근거 방문이 모두 취소되면 회수 + 벗김 — "체크인 → 즉시 취소"로 이벤트 아이템을 얻을 수 없다
            cancel(me, null, "KR-11050");
            awaitOwned(me);
            await().atMost(WAIT).untilAsserted(() -> assertThat(eventItems(me)).isEmpty());
            awaitSlot(me, "SLOT_PET", null);
        } finally {
            clock.set(original);
        }
    }

    @Test
    void 세트_완성_처리_때_지금_멤버_중_배경이_없는_사람에게도_준다_P3_1() {
        String owner = register();
        String late = register();
        ExpeditionMap shared = maps.create(ExplorerId.of(owner), "늦은 합류", "KR");
        String mapId = shared.id().value();
        maps.join(ExplorerId.of(late), shared.inviteCode().value());
        await().atMost(WAIT).until(() -> jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE published_at IS NULL AND aggregate_id = ?", Integer.class, mapId) == 0);
        assertThat(owned(late)).isEmpty();

        // 경합 재현: late 의 MemberJoined 는 완성 기록보다 먼저 처리됐고, late 는 완성 시점 수령자 목록에도 없다
        transaction.executeWithoutResult(status ->
            inventories.onSetCompleted(new SetCompleted(mapId, "jiri", owner, clock.instant(), owner, List.of(owner))));

        // 완성자 Inventory 만 그 트랜잭션에서 고치고(R2-5), late 는 ThemeRewardOwed 로 자기 트랜잭션에서 받는다
        assertThat(owned(owner)).containsExactly("set:jiri");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%ThemeRewardOwed'",
            Integer.class, late)).isEqualTo(1);
        awaitOwned(late, "set:jiri");
    }

    @Test
    void 이슈_아이템은_정의가_생기기_전_체크인에_소급되지_않는다_재계산도_같다_Q_R2_1() {
        String me = register();
        checkIn(me, null, "KR-26010"); // 대구 — 이슈 아이템을 만들기 전의 체크인
        awaitOwned(me, "region:KR-26010");
        clock.advance(java.time.Duration.ofMinutes(1));
        String itemId = "event:late-" + UUID.randomUUID().toString().substring(0, 8);
        itemCatalog.register(new RegisterItemCommand(itemId, "대구 사과 핀", "🍎", ItemSlot.BADGE, Rarity.RARE, null,
            null, null, null, GrantRule.Type.PROVINCE_CHECK_IN, "KR-26", null, null));

        recalculate.recalculate(ExplorerId.of(me));
        assertThat(eventItems(me)).as("정의 생성 전 체크인에는 소급 지급 없음").isEmpty();

        clock.advance(java.time.Duration.ofMinutes(1));
        checkIn(me, null, "KR-26020"); // 정의가 생긴 뒤의 대구 체크인 → 지급
        await().atMost(WAIT).untilAsserted(() -> assertThat(eventItems(me)).containsExactly(itemId));
        recalculate.recalculate(ExplorerId.of(me));
        assertThat(eventItems(me)).containsExactly(itemId);
        assertThat(jdbc.queryForList("SELECT region_code FROM owned_item_basis WHERE explorer_id = ? AND item_id = ?",
            String.class, me, itemId)).as("근거는 정의 생성 뒤의 방문만").containsExactly("KR-26020");
    }

    @Test
    void 탈퇴해도_그_지도에서_얻은_아이템은_남는다_P3_17() {
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
        // 재계산도 탈퇴한 지도의 아이템을 지우지 않는다(재생할 수 없는 지도의 근거 유지)
        recalculate.recalculate(ExplorerId.of(member));
        assertThat(owned(member)).containsExactly("region:KR-11060");
    }

    @Test
    void 재계산은_손상된_인벤토리를_복구하고_두_번_돌려도_같다_가방에_없는_착용은_벗긴다() {
        String me = register();
        checkIn(me, null, "KR-11010");
        checkIn(me, null, "KR-21090");
        awaitOwned(me, "region:KR-11010", "region:KR-21090");
        awaitSlot(me, "SLOT_HAND", "region:KR-11010");
        jdbc.update("UPDATE owned_item SET favorite = TRUE WHERE explorer_id = ? AND item_id = 'region:KR-21090'", me);
        Map<String, Object> healthy = inventorySnapshot(me);

        // 손상: 아이템·근거·흔적 일부 삭제 + 가방에 없는 아이템 착용
        jdbc.update("DELETE FROM owned_item_basis WHERE explorer_id = ? AND item_id = 'region:KR-11010'", me);
        jdbc.update("DELETE FROM owned_item WHERE explorer_id = ? AND item_id = 'region:KR-11010'", me);
        jdbc.update("DELETE FROM inventory_visit WHERE explorer_id = ?", me);
        jdbc.update("UPDATE scene SET slot_hat = 'region:KR-36330' WHERE explorer_id = ?", me);

        recalculate.recalculate(ExplorerId.of(me));
        assertThat(inventorySnapshot(me)).isEqualTo(healthy);
        assertThat(scene(me).get("SLOT_HAT")).as("가방에 없는 착용은 벗긴다").isNull();
        assertThat(scene(me).get("SLOT_HAND")).as("나머지 착용 선택은 유지").isEqualTo("region:KR-11010");

        recalculate.recalculate(ExplorerId.of(me));
        assertThat(inventorySnapshot(me)).isEqualTo(healthy);
        assertThat(recalculate.recalculateIfSettled(ExplorerId.of(me))).isTrue();
    }

    @Test
    void 재체크인_뒤_늦게_다시_온_옛_세대_취소는_무시한다() {
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

        // 세대 1 의 취소가 재전달된 상황
        inventories.onVisitCancelled(new VisitCancelled(me, mapId, "KR-11040", Rarity.COMMON, "KR-11", true, 0, false,
            clock.instant(), 1));

        assertThat(owned(me)).containsExactly("region:KR-11040");
    }
}
