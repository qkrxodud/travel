package com.kobi.territory.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.event.EventSubscriber;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.application.CheckInCommand;
import com.kobi.territory.exploration.application.CheckInService;
import com.kobi.territory.exploration.application.ExplorerService;
import com.kobi.territory.outbox.OutboxDeliveryEntity;
import com.kobi.territory.outbox.OutboxDeliveryRepository;
import com.kobi.territory.outbox.OutboxEventRepository;
import com.kobi.territory.outbox.OutboxRedelivery;
import com.kobi.territory.progression.api.event.SetCompleted;
import com.kobi.territory.progression.application.QuestService;
import com.kobi.territory.progression.application.RecalculateService;
import com.kobi.territory.progression.domain.progress.ExplorerProgress;
import com.kobi.territory.progression.domain.progress.ExplorerProgressRepository;
import com.kobi.territory.progression.domain.progress.XpLedgerEntry;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.IntegrationTestConfig.FaultInjection;
import com.kobi.territory.support.IntegrationTestConfig.PoisonSubscriber;
import com.kobi.territory.support.IntegrationTestConfig.ReplacePause;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.kobi.territory.support.MutableClock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D2: 체크인 → outbox 릴레이(구독자 = 소비 애그리거트) → 진행·도감·퀘스트 반영(awaitility), 멱등, 취소 비대칭,
 * 재체크인 재지급, 독성 구독자 격리, 재계산 = 이벤트 누적.
 * QA 수정 회귀: 취소가 체크인을 앞지르지 않음(P1-1), FAILED 재전달·재계산 복구 규칙(P1-2).
 */
@IntegrationTest
class ProgressionIntegrationTest {

    /** 지리산 둘레: 남원시(일반)·구례군·하동군·산청군·함양군(희귀). 전북·전남·경남. */
    static final List<String> JIRI = List.of("KR-35050", "KR-36330", "KR-38360", "KR-38370", "KR-38380");
    /** 35+45+45+30+30 + 세트 100 */
    static final int JIRI_XP = 285;
    static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired ExplorerService explorers;
    @Autowired CheckInService checkIns;
    @Autowired QuestService quests;
    @Autowired RecalculateService recalculate;
    @Autowired OutboxRedelivery redelivery;
    @Autowired ExplorerProgressRepository progresses;
    @Autowired OutboxEventRepository outbox;
    @Autowired OutboxDeliveryRepository deliveries;
    @Autowired List<EventSubscriber> subscribers;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired ObjectMapper objectMapper;
    @Autowired MutableClock clock;

    @AfterEach
    void clearFaults() {
        FaultInjection.clear();
    }

    private String register() {
        return explorers.registerAnonymous().explorer().id().value();
    }

    private String mapOf(String explorerId) {
        return jdbc.queryForObject("SELECT id FROM expedition_map WHERE owner_id = ?", String.class, explorerId);
    }

    private void checkIn(String explorerId, String code) {
        checkIns.checkIn(new CheckInCommand(ExplorerId.of(explorerId), null, RegionCode.of(code), LocalDate.now(clock),
            null, null));
    }

    private void cancel(String explorerId, String code) {
        checkIns.cancel(ExplorerId.of(explorerId), null, RegionCode.of(code));
    }

    private ExplorerProgress progress(String explorerId) {
        return transaction.execute(status -> progresses.find(ExplorerId.of(explorerId)).orElseThrow());
    }

    private long xp(String explorerId) {
        Long value = jdbc.query("SELECT xp FROM explorer_progress WHERE explorer_id = ?",
            resultSet -> resultSet.next() ? resultSet.getLong(1) : null, explorerId);
        return value == null ? -1 : value;
    }

    /** 이 탐험가 지도·보드의 outbox 가 모두 발행될 때까지(모든 구독자 DELIVERED). */
    private void awaitRelayed(String explorerId) {
        String mapId = mapOf(explorerId);
        await().atMost(WAIT).until(() -> jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE published_at IS NULL AND aggregate_id IN (?, ?)", Integer.class,
            mapId, explorerId) == 0);
    }

    private Map<String, Object> snapshot(String explorerId) {
        String mapId = mapOf(explorerId);
        return Map.of(
            "progress", jdbc.queryForList(
                "SELECT xp, level, streak_months, streak_last_month FROM explorer_progress WHERE explorer_id = ?", explorerId),
            "ledger", jdbc.queryForList("SELECT ref_id, amount FROM xp_ledger WHERE explorer_id = ? ORDER BY ref_id", explorerId),
            "badges", jdbc.queryForList("SELECT badge_id FROM badge_earned WHERE explorer_id = ? ORDER BY badge_id",
                String.class, explorerId),
            "titles", jdbc.queryForList("SELECT title_id FROM title_earned WHERE explorer_id = ? ORDER BY title_id",
                String.class, explorerId),
            "regions", jdbc.queryForList("SELECT region_code, active_map_count, active_map_ids FROM explorer_region "
                + "WHERE explorer_id = ? ORDER BY region_code", explorerId),
            "sets", jdbc.queryForList("SELECT set_id, collected_codes, completed_at FROM set_progress WHERE map_id = ? "
                + "ORDER BY set_id", mapId),
            "quests", jdbc.queryForList("SELECT quest_period, quest_id, current_count, tally, claimed_at FROM quest_progress "
                + "WHERE explorer_id = ? ORDER BY quest_period, quest_id", explorerId));
    }

    @Test
    void 체크인하면_릴레이가_진행_탐험가지역_도감_퀘스트에_반영한다() {
        String explorerId = register();
        JIRI.forEach(code -> checkIn(explorerId, code));

        await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP);
        ExplorerProgress progress = progress(explorerId);
        assertThat(progress.level()).isEqualTo(4);
        assertThat(progress.badges()).containsKeys("first", "set1");
        assertThat(progress.titles()).containsKeys("lv1", "lv3", "set-jiri");
        assertThat(progress.ledger().entries()).extracting(XpLedgerEntry::refId)
            .contains("region:" + explorerId + ":KR-35050#1", "province:" + explorerId + ":KR-35", "set:" + explorerId + ":jiri");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM explorer_region WHERE explorer_id = ? AND active_map_count = 1",
            Integer.class, explorerId)).isEqualTo(5);
        String mapId = mapOf(explorerId);
        assertThat(jdbc.queryForObject("SELECT completed_at FROM set_progress WHERE map_id = ? AND set_id = 'jiri'",
            Timestamp.class, mapId)).isNotNull();
        awaitRelayed(explorerId);
        assertThat(jdbc.queryForObject("SELECT current_count FROM quest_progress WHERE explorer_id = ? AND quest_id = 'm3'",
            Integer.class, explorerId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT current_count FROM quest_progress WHERE explorer_id = ? AND quest_id = 'gun30'",
            Integer.class, explorerId)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT current_count FROM quest_progress WHERE explorer_id = ? AND quest_id = 'mprov'",
            Integer.class, explorerId)).as("전북·전남·경남 첫 방문(탐험가 기준)").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox o JOIN outbox_delivery d ON d.event_id = o.id "
            + "WHERE o.aggregate_id = ? AND d.status = 'DELIVERED' AND d.subscriber LIKE 'progression.%'", Integer.class, mapId))
            .as("RegionVisited 5건 × 진행·도감·퀘스트 3구독자 + SetCompleted 1건 × 진행 + MapCreated 1건 × 진행(루트 선생성, S3-1)")
            .isEqualTo(5 * 3 + 1 + 1);
    }

    @Test
    void 같은_이벤트를_두_번_전달해도_결과는_한_번과_같다() throws Exception {
        String explorerId = register();
        checkIn(explorerId, "KR-38360");
        checkIn(explorerId, "KR-38370");
        await().atMost(WAIT).until(() -> xp(explorerId) == 45 + 30);
        awaitRelayed(explorerId);
        Map<String, Object> before = snapshot(explorerId);

        for (String payload : jdbc.queryForList(
            "SELECT payload FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%RegionVisited'", String.class,
            mapOf(explorerId))) {
            RegionVisited event = objectMapper.readValue(payload, RegionVisited.class);
            subscribers.stream().filter(subscriber -> subscriber.id().startsWith("progression.") && subscriber.accepts(event))
                .forEach(subscriber -> {
                    transaction.executeWithoutResult(status -> subscriber.handle(event));
                    transaction.executeWithoutResult(status -> subscriber.handle(event));
                });
        }
        assertThat(snapshot(explorerId)).isEqualTo(before);
    }

    @Test
    void 취소는_기본_XP만_회수하고_도감_완성_뱃지_퀘스트는_유지_재체크인은_다음_세대로_재지급() {
        String explorerId = register();
        JIRI.forEach(code -> checkIn(explorerId, code));
        await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP);
        String mapId = mapOf(explorerId);

        cancel(explorerId, "KR-38380"); // 함양군(희귀 20)
        await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP - 20);
        awaitRelayed(explorerId);
        ExplorerProgress progress = progress(explorerId);
        assertThat(progress.badges()).containsKeys("first", "set1");
        assertThat(progress.titles()).containsKey("set-jiri");
        assertThat(progress.ledger().has("region:" + explorerId + ":KR-38380#1:revoke")).isTrue();
        assertThat(jdbc.queryForObject("SELECT active_map_count FROM explorer_region WHERE explorer_id = ? "
            + "AND region_code = 'KR-38380'", Integer.class, explorerId)).isZero();
        assertThat(jdbc.queryForObject("SELECT collected_codes FROM set_progress WHERE map_id = ? AND set_id = 'jiri'",
            String.class, mapId)).doesNotContain("KR-38380");
        assertThat(jdbc.queryForObject("SELECT completed_at FROM set_progress WHERE map_id = ? AND set_id = 'jiri'",
            Timestamp.class, mapId)).as("완성 기록 유지").isNotNull();
        assertThat(jdbc.queryForObject("SELECT current_count FROM quest_progress WHERE explorer_id = ? AND quest_id = 'gun30'",
            Integer.class, explorerId)).as("퀘스트 진행 유지").isEqualTo(4);

        checkIn(explorerId, "KR-38380");
        await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP); // 기본 20만 다시(선점·시·도·세트는 이미)
        awaitRelayed(explorerId);
        assertThat(progress(explorerId).ledger().has("region:" + explorerId + ":KR-38380#2")).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE ref_id LIKE ?", Integer.class,
            "set:" + explorerId + ":%")).isEqualTo(1);
    }

    @Test
    void 취소가_체크인을_앞지르지_않는다_체크인_처리_실패를_주입해도_결과는_정상_흐름과_같다_QA_P1_1() {
        String control = register();
        checkIn(control, "KR-38360");
        cancel(control, "KR-38360");
        awaitRelayed(control);

        String faulty = register();
        FaultInjection.failNext("progression.progress", RegionVisited.class, faulty, 1);
        FaultInjection.failNext("progression.collection-book", RegionVisited.class, faulty, 1);
        checkIn(faulty, "KR-38360");
        cancel(faulty, "KR-38360");
        awaitRelayed(faulty);

        assertThat(xp(faulty)).as("기본 20 회수, 시·도·선점 보너스만 남음").isEqualTo(xp(control)).isEqualTo(25);
        assertThat(jdbc.queryForObject("SELECT active_map_count FROM explorer_region WHERE explorer_id = ?",
            Integer.class, faulty)).isZero();
        assertThat(jdbc.queryForObject("SELECT collected_codes FROM set_progress WHERE map_id = ? AND set_id = 'jiri'",
            String.class, mapOf(faulty))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_delivery d JOIN outbox o ON o.id = d.event_id "
            + "WHERE o.aggregate_id = ? AND o.event_type LIKE '%RegionVisited' AND d.subscriber = 'progression.progress'",
            Integer.class, mapOf(faulty))).as("한 번 실패 후 성공").isEqualTo(2);
    }

    @Test
    void 독성_구독자는_5회_후_FAILED_로_그_단위만_멈추고_재전달하면_풀린다() {
        String explorerId = register();
        PoisonSubscriber.POISONED.add(explorerId);
        try {
            checkIn(explorerId, "KR-38360");
            await().atMost(WAIT).until(() -> xp(explorerId) == 45);
            Long eventId = jdbc.queryForObject("SELECT id FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%RegionVisited'",
                Long.class, mapOf(explorerId));
            OutboxDeliveryEntity.Key poisonKey = new OutboxDeliveryEntity.Key(eventId, PoisonSubscriber.ID);
            await().atMost(WAIT).until(() -> deliveries.findById(poisonKey).map(OutboxDeliveryEntity::failed).orElse(false));
            OutboxDeliveryEntity poison = deliveries.findById(poisonKey).orElseThrow();
            assertThat(poison.attempts()).isEqualTo(5);
            assertThat(poison.lastError()).contains("독성 이벤트");
            assertThat(deliveries.findByEventId(eventId)).filteredOn(delivery -> delivery.subscriber().startsWith("progression."))
                .hasSize(3).allSatisfy(delivery -> {
                    assertThat(delivery.delivered()).isTrue();
                    assertThat(delivery.attempts()).as("다른 구독자는 한 번만 처리됨").isEqualTo(1);
                });
            assertThat(outbox.findById(eventId).orElseThrow().publishedAt()).as("FAILED 가 남아 미발행").isNull();

            // 같은 지도의 다음 이벤트: 다른 구독자 단위는 진행, 독성 구독자 단위는 순서를 지켜 멈춘다
            checkIn(explorerId, "KR-38370");
            await().atMost(WAIT).until(() -> xp(explorerId) == 45 + 30);
            Long nextId = jdbc.queryForObject("SELECT MAX(id) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%RegionVisited'",
                Long.class, mapOf(explorerId));
            assertThat(deliveries.findById(new OutboxDeliveryEntity.Key(nextId, PoisonSubscriber.ID))).isEmpty();

            // 원인 해소 + 재전달 → 순서대로 전달되고 발행 완료
            PoisonSubscriber.POISONED.remove(explorerId);
            assertThat(redelivery.redeliverFailed(eventId, PoisonSubscriber.ID)).isEqualTo(1);
            awaitRelayed(explorerId);
            assertThat(deliveries.findById(new OutboxDeliveryEntity.Key(nextId, PoisonSubscriber.ID)).orElseThrow().delivered())
                .isTrue();
        } finally {
            PoisonSubscriber.POISONED.remove(explorerId);
        }
    }

    @Test
    void SetCompleted_전달이_FAILED_가_되어도_재전달하면_세트_보너스와_칭호가_반영된다_QA_P1_2() {
        String explorerId = register();
        FaultInjection.failNext("progression.progress", SetCompleted.class, explorerId, 5);
        JIRI.forEach(code -> checkIn(explorerId, code));
        await().atMost(WAIT).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery d JOIN outbox o "
            + "ON o.id = d.event_id WHERE o.aggregate_id = ? AND d.status = 'FAILED'", Integer.class, mapOf(explorerId)) == 1);
        assertThat(xp(explorerId)).isEqualTo(JIRI_XP - 100);
        assertThat(progress(explorerId).titles()).doesNotContainKey("set-jiri");

        assertThat(redelivery.redeliverFailed(null, "progression.progress")).isGreaterThanOrEqualTo(1);
        await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP);
        awaitRelayed(explorerId);
        assertThat(progress(explorerId).titles()).containsKey("set-jiri");
    }

    @Test
    void 재계산_복구_규칙_잃은_세트_보너스_칭호_퀘스트_XP_를_되살린다_QA_P1_2() {
        String explorerId = register();
        JIRI.forEach(code -> checkIn(explorerId, code));
        await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP);
        quests.claim(ExplorerId.of(explorerId), "m3");
        await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP + 60);
        awaitRelayed(explorerId);
        Map<String, Object> healthy = snapshot(explorerId);

        // 전달 손실을 흉내: 장부의 세트·퀘스트 보상과 세트 칭호를 지운다(완성 기록·받음 기록은 남음)
        jdbc.update("DELETE FROM xp_ledger WHERE explorer_id = ? AND (ref_id LIKE 'set:%' OR ref_id LIKE 'quest:%')", explorerId);
        jdbc.update("DELETE FROM title_earned WHERE explorer_id = ? AND title_id = 'set-jiri'", explorerId);

        recalculate.recalculate(ExplorerId.of(explorerId));

        assertThat(xp(explorerId)).isEqualTo(JIRI_XP + 60);
        assertThat(progress(explorerId).titles()).containsKey("set-jiri");
        Map<String, Object> recovered = snapshot(explorerId);
        for (String key : List.of("progress", "badges", "titles", "regions", "sets", "quests")) {
            assertThat(recovered.get(key)).as(key).isEqualTo(healthy.get(key));
        }
    }

    @Test
    void 재계산_결과는_이벤트_누적_결과와_같다_보상_받은_퀘스트는_유지() {
        String explorerId = register();
        JIRI.forEach(code -> checkIn(explorerId, code));
        checkIn(explorerId, "KR-11010");
        await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP + 35);
        quests.claim(ExplorerId.of(explorerId), "m3");
        await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP + 35 + 60);
        awaitRelayed(explorerId);
        Map<String, Object> accumulated = snapshot(explorerId);

        recalculate.recalculate(ExplorerId.of(explorerId));

        assertThat(snapshot(explorerId)).isEqualTo(accumulated);
    }

    @Test
    void 취소가_섞여도_재계산은_XP_레벨_뱃지_칭호_지역_도감완성_퀘스트를_누적과_같게_만든다() {
        String explorerId = register();
        JIRI.forEach(code -> checkIn(explorerId, code));
        cancel(explorerId, "KR-38380");       // 세트 완성 후 취소(완성 유지, 기본 XP 회수)
        cancel(explorerId, "KR-35050");       // 전북 유일 지역 취소(시·도 보너스는 유지)
        checkIn(explorerId, "KR-35050");      // 재체크인(다음 세대)
        await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP - 20);
        awaitRelayed(explorerId);
        Map<String, Object> accumulated = snapshot(explorerId);

        recalculate.recalculate(ExplorerId.of(explorerId));

        Map<String, Object> recalculated = snapshot(explorerId);
        for (String key : List.of("progress", "badges", "titles", "regions", "sets", "quests")) {
            assertThat(recalculated.get(key)).as(key).isEqualTo(accumulated.get(key));
        }
        assertThat(jdbc.queryForList("SELECT ref_id FROM xp_ledger WHERE explorer_id = ? AND source <> 'REGION_BASE' "
            + "ORDER BY ref_id", String.class, explorerId)).hasSize(3 + 5 + 1); // 시·도 3, 선점 5, 세트 1
    }

    @Test
    void 진행_저장은_루트_값이_그대로여도_version_을_올린다_QA_I_2() {
        String explorerId = register();
        checkIn(explorerId, "KR-38360");
        await().atMost(WAIT).until(() -> xp(explorerId) == 45);
        awaitRelayed(explorerId);
        long before = jdbc.queryForObject("SELECT version FROM explorer_progress WHERE explorer_id = ?", Long.class, explorerId);

        transaction.executeWithoutResult(status ->
            progresses.save(progresses.find(ExplorerId.of(explorerId)).orElseThrow())); // 아무것도 안 바뀐 저장

        long after = jdbc.queryForObject("SELECT version FROM explorer_progress WHERE explorer_id = ?", Long.class, explorerId);
        assertThat(after).isGreaterThan(before);
    }

    @Test
    void 재계산_replace_도_version_을_올리고_장부를_시간_순으로_다시_넣는다_QA_S_1_S_3() {
        String explorerId = register();
        JIRI.forEach(code -> {
            clock.advance(Duration.ofSeconds(1)); // 실제처럼 체크인마다 처리 시각이 다르게
            checkIn(explorerId, code);
        });
        await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP);
        awaitRelayed(explorerId);
        long versionBefore = jdbc.queryForObject("SELECT version FROM explorer_progress WHERE explorer_id = ?", Long.class,
            explorerId);
        List<String> recentBefore = progress(explorerId).ledger().recent(10).stream().map(XpLedgerEntry::refId).toList();

        recalculate.recalculate(ExplorerId.of(explorerId)); // 루트 값(xp·레벨·칭호·스트릭)은 그대로인 재계산

        long versionAfter = jdbc.queryForObject("SELECT version FROM explorer_progress WHERE explorer_id = ?", Long.class,
            explorerId);
        assertThat(versionAfter).as("replace 도 version 증가").isGreaterThan(versionBefore);
        assertThat(progress(explorerId).ledger().recent(10).stream().map(XpLedgerEntry::refId).toList())
            .as("GET /progress recentXp 순서 유지").isEqualTo(recentBefore);
        assertThat(xp(explorerId)).isEqualTo(JIRI_XP);
    }

    @Test
    void 재계산이_읽은_뒤_커밋된_도감_반영분을_재계산이_덮어쓰지_않는다_QA_S2_1() throws Exception {
        String explorerId = register();
        String mapId = mapOf(explorerId);
        List.of("KR-35050", "KR-36330", "KR-38360").forEach(code -> checkIn(explorerId, code));
        await().atMost(WAIT).until(() -> xp(explorerId) == 35 + 45 + 45);
        awaitRelayed(explorerId);

        ReplacePause.arm();
        ExecutorService recalculation = Executors.newSingleThreadExecutor();
        try {
            Future<?> running = recalculation.submit(() -> recalculate.recalculate(ExplorerId.of(explorerId)));
            assertThat(ReplacePause.awaitReached(Duration.ofSeconds(20))).as("재계산이 도감 replace 직전에 멈춤").isTrue();

            checkIn(explorerId, "KR-38370"); // 재계산이 도감을 읽은 뒤의 체크인
            await().atMost(Duration.ofSeconds(40)).until(() -> jdbc.queryForObject(
                "SELECT collected_codes FROM set_progress WHERE map_id = ? AND set_id = 'jiri'", String.class, mapId)
                .contains("KR-38370")); // 도감 이벤트 반영이 먼저 커밋됨

            ReplacePause.release();
            running.get(60, TimeUnit.SECONDS); // version 충돌 → 재계산 재시도
        } finally {
            ReplacePause.release();
            recalculation.shutdownNow();
        }
        awaitRelayed(explorerId);
        assertThat(jdbc.queryForObject("SELECT collected_codes FROM set_progress WHERE map_id = ? AND set_id = 'jiri'",
            String.class, mapId)).as("도감 반영분 유지").contains("KR-38370");
        await().atMost(WAIT).until(() -> xp(explorerId) == 35 + 45 + 45 + 30);
    }
}
