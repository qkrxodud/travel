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
        if (explorerId != null) builder.header("X-Explorer-Id", explorerId);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode newExplorer() throws Exception {
        return om.readTree(send("POST", "/explorers", null, null).body());
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
}
