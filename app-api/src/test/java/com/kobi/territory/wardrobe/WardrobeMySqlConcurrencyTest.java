package com.kobi.territory.wardrobe;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
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
 * QA P2-1 회귀(MySQL 8.4): 인벤토리 재계산(POST /dev/recalculate — 진행 다음 꾸미기)을 체크인·취소·장면 편집과 겹쳐 돌려도
 * 전달 실패가 남지 않고, 조용해진 뒤 재계산 결과가 이벤트 누적 상태와 같다(루트 선잠금 + version 강제 증가). Docker 가 없으면 skip.
 */
@DisplayName("가방 다시 계산과 겹친 요청")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.h2.console.enabled=false",
    "spring.datasource.hikari.maximum-pool-size=20"
})
@Import(IntegrationTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WardrobeMySqlConcurrencyTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterAll
    static void shutdown() {
        POOL.shutdownNow();
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json");
        if (token != null) builder.header("X-Explorer-Token", token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> snapshot(String explorerId) {
        return Map.of(
            "items", jdbc.queryForList("SELECT item_id, source, acquired_at, favorite FROM owned_item WHERE explorer_id = ? "
                + "ORDER BY item_id", explorerId),
            "basis", jdbc.queryForList("SELECT item_id, region_code, map_id FROM owned_item_basis WHERE explorer_id = ? "
                + "ORDER BY item_id, region_code, map_id", explorerId),
            "visits", jdbc.queryForList("SELECT region_code, map_id, generation, active FROM inventory_visit WHERE explorer_id = ? "
                + "ORDER BY region_code, map_id", explorerId));
    }

    @Test
    @DisplayName("다시 계산이 체크인·취소·장면 편집과 겹쳐 돌아도 전달 실패가 남지 않고, 입은 것은 가방 안에 있으며, 조용해진 뒤 다시 계산해도 같다")
    void recalculationUnderContention() throws Exception {
        JsonNode explorer = om.readTree(send("POST", "/explorers", null, null).body());
        String me = explorer.get("explorerId").asText();
        String token = explorer.get("accessToken").asText();
        String mapId = explorer.get("personalMapId").asText();
        String today = LocalDate.now(clock).toString();
        AtomicBoolean running = new AtomicBoolean(true);
        Future<List<Integer>> recalculations = POOL.submit(() -> {
            List<Integer> statuses = new ArrayList<>();
            while (running.get()) statuses.add(send("POST", "/dev/recalculate", token, null).statusCode());
            return statuses;
        });
        Future<List<Integer>> sceneEdits = POOL.submit(() -> {
            List<Integer> statuses = new ArrayList<>();
            for (int i = 0; running.get(); i++) {
                statuses.add(send("PUT", "/scene", token, "{\"gender\":\"" + (i % 2 == 0 ? "F" : "M") + "\"}").statusCode());
            }
            return statuses;
        });
        try {
            for (String code : List.of("KR-35050", "KR-36330", "KR-38360", "KR-38370", "KR-38380", "KR-11010", "KR-21090")) {
                assertThat(send("POST", "/visits", token, "{\"regionCode\":\"" + code + "\",\"visitDate\":\"" + today + "\"}")
                    .statusCode()).isEqualTo(201);
            }
            assertThat(send("DELETE", "/visits/KR-11010", token, null).statusCode()).isBetween(200, 299);
            Thread.sleep(3000); // 릴레이가 처리하는 동안 재계산·편집을 더 겹친다
        } finally {
            running.set(false);
        }
        List<Integer> recalculated = recalculations.get();
        List<Integer> edited = sceneEdits.get();
        Awaitility.await().atMost(Duration.ofSeconds(60)).until(() -> jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE published_at IS NULL AND aggregate_id IN (?, ?)", Integer.class, mapId, me) == 0);

        assertThat(recalculated).as("겹쳐 돈 재계산").hasSizeGreaterThan(3).containsOnly(200);
        assertThat(edited).allMatch(status -> status == 200 || status == 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery WHERE subscriber LIKE 'wardrobe.%' AND status = 'FAILED'",
            Integer.class)).isZero();
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
            jdbc.queryForList("SELECT item_id FROM owned_item WHERE explorer_id = ? ORDER BY item_id", String.class, me))
            .containsExactly("region:KR-21090", "region:KR-35050", "region:KR-36330", "region:KR-38360", "region:KR-38370",
                "region:KR-38380", "set:jiri"));
        // 착용은 모두 가방 안에 있다
        Map<String, Object> scene = jdbc.queryForMap("SELECT * FROM scene WHERE explorer_id = ?", me);
        List<String> owned = jdbc.queryForList("SELECT item_id FROM owned_item WHERE explorer_id = ?", String.class, me);
        for (String column : List.of("SLOT_HAT", "SLOT_HAND", "SLOT_BADGE", "SLOT_BAG", "SLOT_PET", "SLOT_BG")) {
            Object worn = scene.get(column);
            if (worn != null) assertThat(owned).contains(worn.toString());
        }

        // 조용해진 뒤 재계산 = 누적(두 번 돌려도 같다)
        Map<String, Object> accumulated = snapshot(me);
        assertThat(send("POST", "/dev/recalculate", token, null).statusCode()).isEqualTo(200);
        assertThat(snapshot(me)).isEqualTo(accumulated);
        assertThat(send("POST", "/dev/recalculate", token, null).statusCode()).isEqualTo(200);
        assertThat(snapshot(me)).isEqualTo(accumulated);
        System.out.println("[P2-1 MySQL] 재계산 " + recalculated.size() + "회 · 장면 편집 " + edited.size() + "회(409 "
            + edited.stream().filter(status -> status == 409).count() + ")를 겹쳐 실행 — FAILED 0, 재계산 = 누적");
    }
}
