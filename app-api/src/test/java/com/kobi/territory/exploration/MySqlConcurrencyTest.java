package com.kobi.territory.exploration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.support.IntegrationTestConfig;
import com.kobi.territory.support.MutableClock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * QA P1-1 회귀: MySQL(기본 REPEATABLE READ)에서 동시 체크인이 지도 단위로 직렬화되는지 실제 HTTP로 검증한다.
 * Docker가 없으면 skip된다. 기본 빌드(./gradlew build)에 포함된다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.h2.console.enabled=false",
    "spring.datasource.hikari.maximum-pool-size=20"
})
@Import(IntegrationTestConfig.class)
// 컨테이너가 멈추기 전에 컨텍스트(outbox 릴레이 스케줄러)를 닫는다 — 안 그러면 JVM 끝까지 죽은 DB에 접속을 시도한다.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MySqlConcurrencyTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private static final int THREADS = 12;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(THREADS);

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired RegionCatalog catalog;
    @Autowired com.kobi.territory.exploration.application.MapPurgeJob purgeJob;

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterAll
    static void shutdown() {
        POOL.shutdownNow();
    }

    record Res(int status, String code) {}

    private HttpResponse<String> send(String method, String path, String explorerId, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json");
        if (explorerId != null) builder.header("X-Explorer-Token", tokens.getOrDefault(explorerId, explorerId));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** explorerId → 접근 토큰(3단계 결정 2). 테스트 본문은 explorerId 로 쓰고 요청 헤더만 토큰으로 바꾼다. */
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    private JsonNode newExplorer() throws Exception {
        JsonNode explorer = om.readTree(send("POST", "/explorers", null, null).body());
        tokens.put(explorer.get("explorerId").asText(), explorer.get("accessToken").asText());
        return explorer;
    }

    /** 모든 요청을 래치로 동시에 출발시킨다. */
    private List<Res> concurrentCheckIns(String explorerId, List<String> codes) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        String today = LocalDate.now(clock).toString();
        List<Future<Res>> futures = new ArrayList<>();
        for (String code : codes) {
            futures.add(POOL.submit(() -> {
                go.await();
                HttpResponse<String> response = send("POST", "/visits", explorerId,
                    "{\"regionCode\":\"" + code + "\",\"visitDate\":\"" + today + "\"}");
                String errorCode = response.statusCode() == 201 ? null : om.readTree(response.body()).path("code").asText();
                return new Res(response.statusCode(), errorCode);
            }));
        }
        go.countDown();
        List<Res> out = new ArrayList<>();
        for (Future<Res> future : futures) out.add(future.get());
        return out;
    }

    private static Map<String, Long> tally(List<Res> results) {
        return results.stream().collect(Collectors.groupingBy(res -> res.status() + (res.code() == null ? "" : " " + res.code()),
            Collectors.counting()));
    }

    private List<String> seoul(int count) {
        return catalog.activeRegions().stream().filter(region -> region.provinceCode().equals("KR-11")).limit(count)
            .map(region -> region.code()).toList();
    }

    @Test
    void 실제로_MySQL_REPEATABLE_READ_위에서_돈다() {
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4");
        assertThat(jdbc.queryForObject("SELECT @@GLOBAL.transaction_isolation", String.class)).isEqualTo("REPEATABLE-READ");
    }

    @RepeatedTest(3)
    void 온보딩이_끝난_탐험가의_동시_12건은_정확히_5건만_성공한다() throws Exception {
        String me = newExplorer().get("explorerId").asText();
        assertThat(send("POST", "/dev/explorers/age", me, "{\"hours\":73}").statusCode()).isEqualTo(200);

        var tally = tally(concurrentCheckIns(me, seoul(12)));

        assertThat(tally).containsEntry("201", 5L).containsEntry("422 DAILY_CAP_EXCEEDED", 7L).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE checked_in_by = ?", Integer.class, me))
            .isEqualTo(5);
    }

    @RepeatedTest(3)
    void 같은_시도_4곳_동시_체크인의_nth는_1부터_4까지_유일하고_시도_첫방문은_1건() throws Exception {
        JsonNode ex = newExplorer();
        String me = ex.get("explorerId").asText();
        String mapId = ex.get("personalMapId").asText();

        var tally = tally(concurrentCheckIns(me, seoul(4)));
        assertThat(tally).containsEntry("201", 4L).hasSize(1);

        List<JsonNode> visited = new ArrayList<>();
        for (String payload : jdbc.queryForList(
            "SELECT payload FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%.RegionVisited' ORDER BY id",
            String.class, mapId)) {
            visited.add(om.readTree(payload));
        }
        assertThat(visited).hasSize(4);
        assertThat(visited).extracting(payload -> payload.get("nth").asInt()).containsExactlyInAnyOrder(1, 2, 3, 4);
        assertThat(visited.stream().filter(payload -> payload.get("isFirstInProvince").asBoolean())).hasSize(1);
        assertThat(visited).allSatisfy(payload -> assertThat(payload.get("isFirstClaim").asBoolean()).isTrue());
        // 커밋 순서(outbox id 순)대로 nth 가 1,2,3,4 이고 첫 건만 시·도 첫 방문
        assertThat(visited).extracting(payload -> payload.get("nth").asInt()).containsExactly(1, 2, 3, 4);
        assertThat(visited.get(0).get("isFirstInProvince").asBoolean()).isTrue();
    }

    @Test
    void 같은_지역_동시_6건은_1건만_성공하고_나머지는_DUPLICATE_VISIT() throws Exception {
        String me = newExplorer().get("explorerId").asText();
        var tally = tally(concurrentCheckIns(me, java.util.Collections.nCopies(6, "KR-11010")));
        assertThat(tally).containsEntry("201", 1L).containsEntry("409 DUPLICATE_VISIT", 5L).hasSize(2);
    }

    @Test
    void 개인_지도_체크인_수정_취소가_MySQL에서_동작한다() throws Exception {
        String me = newExplorer().get("explorerId").asText();
        String today = LocalDate.now(clock).toString();
        assertThat(send("POST", "/visits", me, "{\"regionCode\":\"KR-37430\",\"visitDate\":\"" + today + "\"}")
            .statusCode()).isEqualTo(201);
        assertThat(send("PATCH", "/visits/KR-37430", me, "{\"memo\":\"독도\"}").statusCode()).isEqualTo(200);
        assertThat(send("DELETE", "/visits/KR-37430", me, null).statusCode()).isEqualTo(204);
        JsonNode territory = om.readTree(send("GET", "/territory", me, null).body());
        assertThat(territory.get("conquest").get("visited").asInt()).isZero();
    }

    @Test
    void 진행_V2와_구독자별_릴레이가_MySQL에서_동작한다() throws Exception {
        String me = newExplorer().get("explorerId").asText();
        var tally = tally(concurrentCheckIns(me, seoul(4)));
        assertThat(tally).containsEntry("201", 4L).hasSize(1);
        // 일반 4곳: 기본 10×4 + 서울 첫 발 15 + 선점 10×4 = 95 (릴레이로 비동기 반영)
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            JsonNode progress = om.readTree(send("GET", "/progress", me, null).body());
            assertThat(progress.get("xp").asInt()).isEqualTo(95);
        });
        assertThat(send("DELETE", "/visits/" + seoul(1).get(0), me, null).statusCode()).isEqualTo(204);
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
            assertThat(om.readTree(send("GET", "/progress", me, null).body()).get("xp").asInt()).isEqualTo(85));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM explorer_region WHERE explorer_id = ? AND active_map_count > 0",
            Integer.class, me)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery WHERE status <> 'DELIVERED'", Integer.class))
            .isZero();
    }


    @Test
    void 칭호_선택_PUT_경합_중에도_체크인_진행이_손실되지_않는다_QA_P1_2() throws Exception {
        String me = newExplorer().get("explorerId").asText();
        String mapId = jdbc.queryForObject("SELECT id FROM expedition_map WHERE owner_id = ?", String.class, me);
        AtomicBoolean running = new AtomicBoolean(true);
        List<Future<Integer>> writers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            writers.add(POOL.submit(() -> {
                int sent = 0;
                while (running.get()) {
                    // 선택·해제를 번갈아 보내 매번 explorer_progress 행(version)이 실제로 바뀌게 한다
                    send("PUT", "/progress/title", me, sent % 2 == 0 ? "{\"titleId\":\"lv1\"}" : "{\"titleId\":null}");
                    sent++;
                }
                return sent;
            }));
        }
        String today = LocalDate.now(clock).toString();
        try {
            for (String code : List.of("KR-35050", "KR-36330", "KR-38360", "KR-38370", "KR-38380")) {
                assertThat(send("POST", "/visits", me, "{\"regionCode\":\"" + code + "\",\"visitDate\":\"" + today + "\"}")
                    .statusCode()).isEqualTo(201);
            }
            Thread.sleep(3000); // 경합을 몇 초 더 유지
        } finally {
            running.set(false);
        }
        int puts = 0;
        for (Future<Integer> writer : writers) puts += writer.get();
        assertThat(puts).as("경합을 만든 PUT 수").isGreaterThan(10);

        Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
            assertThat(om.readTree(send("GET", "/progress", me, null).body()).get("xp").asInt()).isEqualTo(285));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery d JOIN outbox o ON o.id = d.event_id "
            + "WHERE o.aggregate_id IN (?, ?) AND d.status = 'FAILED'", Integer.class, mapId, me)).isZero();
        Integer conflicts = jdbc.queryForObject("SELECT COALESCE(SUM(d.conflicts), 0) FROM outbox_delivery d JOIN outbox o "
            + "ON o.id = d.event_id WHERE o.aggregate_id IN (?, ?)", Integer.class, mapId, me);
        System.out.println("[QA P1-2] PUT " + puts + "건 경합 중 릴레이 낙관적 락 충돌 " + conflicts + "회 — 전부 재시도로 흡수");
    }

    @Test
    void 재계산과_릴레이가_동시에_돌아도_릴레이_반영분이_사라지지_않는다_QA_S_1() throws Exception {
        String me = newExplorer().get("explorerId").asText();
        String mapId = jdbc.queryForObject("SELECT id FROM expedition_map WHERE owner_id = ?", String.class, me);
        AtomicBoolean running = new AtomicBoolean(true);
        Future<List<Integer>> recalculations = POOL.submit(() -> {
            List<Integer> statuses = new ArrayList<>();
            while (running.get()) statuses.add(send("POST", "/dev/recalculate", me, null).statusCode());
            return statuses;
        });
        String today = LocalDate.now(clock).toString();
        try {
            for (String code : List.of("KR-35050", "KR-36330", "KR-38360", "KR-38370", "KR-38380")) {
                assertThat(send("POST", "/visits", me, "{\"regionCode\":\"" + code + "\",\"visitDate\":\"" + today + "\"}")
                    .statusCode()).isEqualTo(201);
            }
            Thread.sleep(3000); // 릴레이가 이벤트를 처리하는 동안 재계산을 몇 초 더 겹쳐 돌린다
        } finally {
            running.set(false);
        }
        List<Integer> statuses = recalculations.get();
        Awaitility.await().atMost(Duration.ofSeconds(60)).until(() -> jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE published_at IS NULL AND aggregate_id IN (?, ?)", Integer.class, mapId, me) == 0);
        System.out.println("[QA S-1] 실패 전달: " + jdbc.queryForList("SELECT d.subscriber, d.status, d.attempts, d.conflicts, "
            + "d.last_error FROM outbox_delivery d JOIN outbox o ON o.id = d.event_id WHERE o.aggregate_id IN (?, ?) "
            + "AND (d.attempts > 1 OR d.conflicts > 0)", mapId, me));
        assertThat(statuses).as("겹쳐 돈 재계산 수").hasSizeGreaterThan(3).containsOnly(200);

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(om.readTree(send("GET", "/progress", me, null).body()).get("xp").asInt()).isEqualTo(285));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id LIKE 'set:%'",
            Integer.class, me)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM explorer_region WHERE explorer_id = ? AND active_map_count = 1",
            Integer.class, me)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery d JOIN outbox o ON o.id = d.event_id "
            + "WHERE o.aggregate_id IN (?, ?) AND d.status = 'FAILED'", Integer.class, mapId, me)).isZero();
        // 조용해진 뒤 한 번 더 재계산해도 같은 결과(재계산 결과 = 모든 이벤트 반영 상태)
        assertThat(send("POST", "/dev/recalculate", me, null).statusCode()).isEqualTo(200);
        assertThat(om.readTree(send("GET", "/progress", me, null).body()).get("xp").asInt()).isEqualTo(285);
        System.out.println("[QA S-1] 재계산 " + statuses.size() + "회를 릴레이와 겹쳐 실행 — xp 285, FAILED 0");
    }

    // ---- 3단계 QA P1-1: 공유 지도 커맨드 경합(지도 행 잠금) ----------------------------------------------------------

    private String createMap(String owner) throws Exception {
        return om.readTree(send("POST", "/maps", owner, "{\"name\":\"경합 원정대\"}").body()).get("mapId").asText();
    }

    private String inviteOf(String member, String mapId) throws Exception {
        return om.readTree(send("GET", "/maps/" + mapId, member, null).body()).get("inviteCode").asText();
    }

    /** 모든 작업을 래치로 동시에 출발시킨다. */
    private List<Res> concurrently(List<java.util.concurrent.Callable<HttpResponse<String>>> calls) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Res>> futures = new ArrayList<>();
        for (var call : calls) {
            futures.add(POOL.submit(() -> {
                go.await();
                HttpResponse<String> response = call.call();
                String errorCode = response.statusCode() < 300 ? null : om.readTree(response.body()).path("code").asText();
                return new Res(response.statusCode(), errorCode);
            }));
        }
        go.countDown();
        List<Res> out = new ArrayList<>();
        for (Future<Res> future : futures) out.add(future.get());
        return out;
    }

    private int activeMembers(String mapId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM map_member WHERE map_id = ? AND left_at IS NULL", Integer.class, mapId);
    }

    @RepeatedTest(3)
    void 자리가_2개_남은_지도에_4명이_동시에_합류하면_정확히_2명만_들어간다() throws Exception {
        String owner = newExplorer().get("explorerId").asText();
        String mapId = createMap(owner);
        String first = newExplorer().get("explorerId").asText();
        assertThat(send("POST", "/maps/join", first, "{\"inviteCode\":\"" + inviteOf(owner, mapId) + "\"}").statusCode())
            .isEqualTo(200);
        String code = inviteOf(owner, mapId);
        List<String> joiners = new ArrayList<>();
        for (int i = 0; i < 4; i++) joiners.add(newExplorer().get("explorerId").asText());

        var tally = tally(concurrently(joiners.stream().<java.util.concurrent.Callable<HttpResponse<String>>>map(joiner ->
            () -> send("POST", "/maps/join", joiner, "{\"inviteCode\":\"" + code + "\"}")).toList()));

        assertThat(tally).containsEntry("200", 2L).containsEntry("409 MAP_FULL", 2L).hasSize(2);
        assertThat(activeMembers(mapId)).isEqualTo(4);
        assertThat(send("GET", "/maps/" + mapId, owner, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/maps", first, null).statusCode()).isEqualTo(200);
    }

    @RepeatedTest(3)
    void 지도장_넘기기와_그_대상의_탈퇴가_동시에_와도_OWNER는_정확히_1명이고_MemberLeft와_상태가_맞다() throws Exception {
        String owner = newExplorer().get("explorerId").asText();
        String member = newExplorer().get("explorerId").asText();
        String mapId = createMap(owner);
        assertThat(send("POST", "/maps/join", member, "{\"inviteCode\":\"" + inviteOf(owner, mapId) + "\"}").statusCode())
            .isEqualTo(200);

        List<Res> results = concurrently(List.of(
            () -> send("POST", "/maps/" + mapId + "/transfer-owner", owner, "{\"explorerId\":\"" + member + "\"}"),
            () -> send("POST", "/maps/" + mapId + "/leave", member, null)));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM map_member WHERE map_id = ? AND left_at IS NULL AND role = 'OWNER'",
            Integer.class, mapId)).isEqualTo(1);
        int memberLeft = jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%MemberLeft'",
            Integer.class, mapId);
        boolean left = jdbc.queryForObject("SELECT COUNT(*) FROM map_member WHERE map_id = ? AND explorer_id = ? AND left_at IS NOT NULL",
            Integer.class, mapId, member) == 1;
        assertThat(memberLeft).isEqualTo(left ? 1 : 0);
        // 둘 중 하나만 성공한다: 양도가 먼저면 탈퇴가 422(지도장), 탈퇴가 먼저면 양도가 403(멤버 아님)
        assertThat(results.stream().filter(result -> result.status() == 200).count()).isEqualTo(1);
        assertThat(left).isEqualTo(results.get(1).status() == 200);
        assertThat(send("GET", "/maps/" + mapId, left ? owner : member, null).statusCode()).isEqualTo(200);
    }

    @RepeatedTest(3)
    void 유예_종료_배치와_재가입이_동시에_와도_응답과_멤버_행이_맞다() throws Exception {
        String owner = newExplorer().get("explorerId").asText();
        String member = newExplorer().get("explorerId").asText();
        String mapId = createMap(owner);
        String code = inviteOf(owner, mapId);
        assertThat(send("POST", "/maps/join", member, "{\"inviteCode\":\"" + code + "\"}").statusCode()).isEqualTo(200);
        assertThat(send("POST", "/maps/" + mapId + "/leave", member, null).statusCode()).isEqualTo(200);
        clock.advance(Duration.ofDays(8));

        List<Res> results = concurrently(List.of(
            () -> send("POST", "/maps/join", member, "{\"inviteCode\":\"" + code + "\"}"),
            () -> {
                purgeJob.run();
                return send("GET", "/health", null, null);
            }));

        assertThat(results.get(0).status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM map_member WHERE map_id = ? AND explorer_id = ? AND left_at IS NULL",
            Integer.class, mapId, member)).isEqualTo(1);
        assertThat(activeMembers(mapId)).isEqualTo(2);
    }

    // ---- 3단계 QA r2 P1-2: 탈퇴와 동시에 들어온 본인 체크인이 지도에 영구히 남지 않는다 ------------------------------

    private void awaitRelayed(String mapId) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE published_at IS NULL AND aggregate_id = ?", Integer.class, mapId) == 0);
    }

    @RepeatedTest(15)
    void 다른_멤버가_영토를_잠근_사이_탈퇴와_본인_체크인이_동시에_와도_탈퇴자의_방문은_남지_않는다() throws Exception {
        String owner = newExplorer().get("explorerId").asText();
        String leaver = newExplorer().get("explorerId").asText();
        String other = newExplorer().get("explorerId").asText();
        String mapId = createMap(owner);
        String code = inviteOf(owner, mapId);
        for (String joiner : List.of(leaver, other)) {
            assertThat(send("POST", "/maps/join", joiner, "{\"inviteCode\":\"" + code + "\"}").statusCode()).isEqualTo(200);
        }
        String today = LocalDate.now(clock).toString();
        List<String> regions = seoul(3);
        AtomicBoolean ticking = new AtomicBoolean(true);
        Future<?> ticker = POOL.submit(() -> { // 처리 시각이 1ms씩 흐르게(QA 재현 조건)
            while (ticking.get()) {
                clock.advance(Duration.ofMillis(1));
                Thread.sleep(1);
            }
            return null;
        });
        try {
            concurrently(List.of(
                () -> send("POST", "/visits", other, "{\"regionCode\":\"" + regions.get(0) + "\",\"visitDate\":\"" + today
                    + "\",\"mapId\":\"" + mapId + "\"}"),
                () -> send("POST", "/visits", other, "{\"regionCode\":\"" + regions.get(1) + "\",\"visitDate\":\"" + today
                    + "\",\"mapId\":\"" + mapId + "\"}"),
                () -> send("POST", "/maps/" + mapId + "/leave", leaver, null),
                () -> send("POST", "/visits", leaver, "{\"regionCode\":\"" + regions.get(2) + "\",\"visitDate\":\"" + today
                    + "\",\"mapId\":\"" + mapId + "\"}")));
        } finally {
            ticking.set(false);
            ticker.get();
        }
        awaitRelayed(mapId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ? AND checked_in_by = ? AND hidden_at IS NULL",
            Integer.class, mapId, leaver)).as("탈퇴자의 보이는 방문").isZero();

        clock.advance(Duration.ofDays(8));
        purgeJob.run();
        awaitRelayed(mapId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit v WHERE v.map_id = ? AND NOT EXISTS (SELECT 1 FROM map_member m "
            + "WHERE m.map_id = v.map_id AND m.explorer_id = v.checked_in_by AND m.left_at IS NULL)", Integer.class, mapId))
            .as("유예 종료 뒤 비멤버 방문").isZero();
    }
}
