package com.kobi.territory.exploration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.catalog.api.RegionCatalog;
import com.kobi.territory.support.IntegrationTestConfig;
import com.kobi.territory.support.MutableClock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json");
        if (explorerId != null) b.header("X-Explorer-Id", explorerId);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
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
                var r = send("POST", "/visits", explorerId,
                    "{\"regionCode\":\"" + code + "\",\"visitDate\":\"" + today + "\"}");
                String c = r.statusCode() == 201 ? null : om.readTree(r.body()).path("code").asText();
                return new Res(r.statusCode(), c);
            }));
        }
        go.countDown();
        List<Res> out = new ArrayList<>();
        for (var f : futures) out.add(f.get());
        return out;
    }

    private static Map<String, Long> tally(List<Res> rs) {
        return rs.stream().collect(Collectors.groupingBy(r -> r.status() + (r.code() == null ? "" : " " + r.code()),
            Collectors.counting()));
    }

    private List<String> seoul(int n) {
        return catalog.activeRegions().stream().filter(r -> r.provinceCode().equals("KR-11")).limit(n)
            .map(r -> r.code()).toList();
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
        assertThat(visited).extracting(p -> p.get("nth").asInt()).containsExactlyInAnyOrder(1, 2, 3, 4);
        assertThat(visited.stream().filter(p -> p.get("isFirstInProvince").asBoolean())).hasSize(1);
        assertThat(visited).allSatisfy(p -> assertThat(p.get("isFirstClaim").asBoolean()).isTrue());
        // 커밋 순서(outbox id 순)대로 nth 가 1,2,3,4 이고 첫 건만 시·도 첫 방문
        assertThat(visited).extracting(p -> p.get("nth").asInt()).containsExactly(1, 2, 3, 4);
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
        JsonNode t = om.readTree(send("GET", "/territory", me, null).body());
        assertThat(t.get("conquest").get("visited").asInt()).isZero();
    }

}
