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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D2: 체크인 → outbox 릴레이(구독자 = 소비 애그리거트) → 진행·도감·퀘스트 반영(awaitility), 멱등, 취소 비대칭,
 * 재체크인 재지급, 독성 구독자 격리, 재계산 = 이벤트 누적.
 * QA 수정 회귀: 취소가 체크인을 앞지르지 않음(P1-1), FAILED 재전달·재계산 복구 규칙(P1-2), 저장 version(I-2), replace version·장부
 * 순서(S-1·S-3), 재계산이 도감 반영을 덮어쓰지 않음(S2-1).
 */
@IntegrationTest
@DisplayName("칠한 곳이 진행·도감·퀘스트로 이어지기")
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

    /** 지리산 둘레를 모두 칠하고 경험치가 다 반영될 때까지 기다린다. */
    private String 지리산_둘레를_칠한_탐험가() {
        String explorerId = register();
        JIRI.forEach(code -> checkIn(explorerId, code));
        await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP);
        return explorerId;
    }

    @Nested
    @DisplayName("지리산 둘레를 모두 칠하면")
    class AfterCheckIns {

        @Test
        @DisplayName("진행에 기본·시·도 첫 발·테마 경험치가 장부로 쌓이고 레벨·뱃지·칭호가 따라온다")
        void progressAccumulates() {
            String explorerId = 지리산_둘레를_칠한_탐험가();
            ExplorerProgress progress = progress(explorerId);
            assertThat(progress.level()).isEqualTo(4);
            assertThat(progress.badges()).containsKeys("first", "set1");
            assertThat(progress.titles()).containsKeys("lv1", "lv3", "set-jiri");
            assertThat(progress.ledger().entries()).extracting(XpLedgerEntry::refId)
                .contains("region:" + explorerId + ":KR-35050#1", "province:" + explorerId + ":KR-35", "set:" + explorerId + ":jiri");
        }

        @Test
        @DisplayName("탐험가 단위로 칠한 다섯 곳이 기록된다")
        void explorerRegionsRecorded() {
            String explorerId = 지리산_둘레를_칠한_탐험가();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM explorer_region WHERE explorer_id = ? AND active_map_count = 1",
                Integer.class, explorerId)).isEqualTo(5);
        }

        @Test
        @DisplayName("지도의 도감에 지리산 테마 완성이 기록된다")
        void themeCompleted() {
            String explorerId = 지리산_둘레를_칠한_탐험가();
            assertThat(jdbc.queryForObject("SELECT completed_at FROM set_progress WHERE map_id = ? AND set_id = 'jiri'",
                Timestamp.class, mapOf(explorerId))).isNotNull();
        }

        @Test
        @DisplayName("퀘스트는 조건에 맞는 지역을 한 번씩 세고, 처음 가는 시·도는 탐험가 기준으로 센다")
        void questsCount() {
            String explorerId = 지리산_둘레를_칠한_탐험가();
            awaitRelayed(explorerId);
            assertThat(jdbc.queryForObject("SELECT current_count FROM quest_progress WHERE explorer_id = ? AND quest_id = 'm3'",
                Integer.class, explorerId)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT current_count FROM quest_progress WHERE explorer_id = ? AND quest_id = 'gun30'",
                Integer.class, explorerId)).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT current_count FROM quest_progress WHERE explorer_id = ? AND quest_id = 'mprov'",
                Integer.class, explorerId)).as("전북·전남·경남 첫 방문(탐험가 기준)").isEqualTo(1);
        }

        @Test
        @DisplayName("진행·도감·퀘스트가 각자 한 번씩 소식을 받는다")
        void eachConsumerReceivesOnce() {
            String explorerId = 지리산_둘레를_칠한_탐험가();
            awaitRelayed(explorerId);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox o JOIN outbox_delivery d ON d.event_id = o.id "
                + "WHERE o.aggregate_id = ? AND d.status = 'DELIVERED' AND d.subscriber LIKE 'progression.%'", Integer.class,
                mapOf(explorerId)))
                .as("RegionVisited 5건 × 진행·도감·퀘스트 3구독자 + SetCompleted 1건 × 진행 + MapCreated 1건 × 진행(루트 선생성, S3-1)")
                .isEqualTo(5 * 3 + 1 + 1);
        }
    }

    @Nested
    @DisplayName("같은 소식이 다시 오거나 처리가 흔들릴 때")
    class Redelivery {

        @Test
        @DisplayName("같은 소식을 두 번 받아도 결과는 한 번 받은 것과 같다")
        void duplicateDeliveryIsNoOp() throws Exception {
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
        @DisplayName("칠함 처리가 한 번 실패해도 뒤따른 취소가 앞지르지 않아 정상 흐름과 결과가 같다")
        void cancelDoesNotOvertakeCheckIn() {
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

    }

    @Nested
    @DisplayName("테마를 완성한 뒤 한 곳을 취소하면")
    class CancelAfterCompletion {

        @Test
        @DisplayName("그곳의 기본 경험치만 회수되고 뱃지·칭호·테마 완성·퀘스트 진행은 남는다")
        void onlyBaseXpRevoked() {
            String explorerId = 지리산_둘레를_칠한_탐험가();
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
        }

        @Test
        @DisplayName("다시 칠하면 다음 회차로 기본 경험치만 다시 받고 테마 보너스는 한 번뿐이다")
        void recheckInRegrantsBaseOnly() {
            String explorerId = 지리산_둘레를_칠한_탐험가();
            cancel(explorerId, "KR-38380");
            await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP - 20);
            checkIn(explorerId, "KR-38380");
            await().atMost(WAIT).until(() -> xp(explorerId) == JIRI_XP);
            awaitRelayed(explorerId);
            assertThat(progress(explorerId).ledger().has("region:" + explorerId + ":KR-38380#2")).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE ref_id LIKE ?", Integer.class,
                "set:" + explorerId + ":%")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("전달이 계속 실패하면")
    class DeliveryFailures {

        @Test
        @DisplayName("한 구독자가 계속 실패하면 다섯 번 뒤 그 구독자 몫만 멈추고, 원인을 고쳐 다시 보내면 밀린 소식까지 순서대로 전달된다")
        void poisonedSubscriberStopsOnlyItsLane() {
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
        @DisplayName("테마 완성 소식 전달이 멈춰도 다시 보내면 테마 보너스와 칭호가 반영된다")
        void redeliveredThemeCompletion() {
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

    }

    @Nested
    @DisplayName("진행을 다시 계산하면")
    class Recalculation {

        @Test
        @DisplayName("잃어버린 테마 보너스·칭호·받은 퀘스트 경험치를 되살린다")
        void recoversLostRewards() {
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
        @DisplayName("결과는 소식을 차례로 받아 쌓은 결과와 같고 받은 퀘스트 보상도 그대로다")
        void equalsAccumulated() {
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
        @DisplayName("취소와 다시 칠하기가 섞여도 경험치·레벨·뱃지·칭호·지역·도감 완성·퀘스트가 쌓인 결과와 같다")
        void equalsAccumulatedWithCancels() {
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
        @DisplayName("바뀐 값이 없는 저장도 진행 기록의 판을 올려 겹친 저장을 알아챌 수 있다")
        void saveAlwaysBumpsRevision() {
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
        @DisplayName("다시 계산도 진행 기록의 판을 올리고 최근 경험치 내역의 순서를 지킨다")
        void recalculationBumpsRevisionAndKeepsOrder() {
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
        @DisplayName("다시 계산이 도감을 읽은 뒤 들어온 체크인 반영을 덮어쓰지 않는다")
        void doesNotOverwriteLaterCollectionUpdate() throws Exception {
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
}
