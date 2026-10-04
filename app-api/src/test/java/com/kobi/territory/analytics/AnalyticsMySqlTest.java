package com.kobi.territory.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.analytics.domain.actor.ExplorerHasher;
import com.kobi.territory.analytics.infra.entity.CohortRow;
import com.kobi.territory.analytics.infra.entity.DailyBreakdownRow;
import com.kobi.territory.analytics.infra.entity.DailyMetricRow;
import com.kobi.territory.analytics.infra.entity.ExplorerJourneyRow;
import com.kobi.territory.analytics.infra.entity.TrackedEventRow;
import com.kobi.territory.analytics.infra.entity.VisitorRow;
import com.kobi.territory.analytics.infra.repository.JdbcMetricsReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
 * 10단계 분석 — MySQL(기본 REPEATABLE READ) + 실제 HTTP. 동시 수집(같은 방문의 첫 기록·탐험가 연결, 레이트 리밋 경계)과 대량 이벤트의 일 배치·
 * 지표 조회 시간. Docker 가 없으면 건너뛴다. 걸린 시간은 표준 출력([분석 성능])으로 남긴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.h2.console.enabled=false",
    "spring.datasource.hikari.maximum-pool-size=30",
    // 레이트 리밋 경계를 재기 쉽게 — 몰아서 20번, 다시 차는 속도는 거의 0
    "territory.analytics.ingest.rate-limit.visitor-burst=20",
    "territory.analytics.ingest.rate-limit.visitor-per-minute=1"
})
@Import(IntegrationTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("분석 이벤트가 몰리고 쌓일 때")
class AnalyticsMySqlTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private static final ExecutorService POOL = Executors.newFixedThreadPool(16);
    private static final String ADMIN_TOKEN = "local-admin-token";

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterAll
    static void shutdown() {
        POOL.shutdownNow();
    }

    @BeforeEach
    void clean() {
        List.of("analytics_event", "analytics_visitor", "analytics_explorer", "analytics_daily_breakdown", "analytics_daily",
            "analytics_cohort").forEach(table -> jdbc.update("DELETE FROM " + table));
    }

    // ---- 준비 문장 ---------------------------------------------------------------------------------------------

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String 탐험가_토큰() throws Exception {
        HttpResponse<String> created = send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/explorers"))
            .POST(HttpRequest.BodyPublishers.noBody()).build());
        return om.readTree(created.body()).get("accessToken").asText();
    }

    private int 보낸다(String visitorId, String token, int events) throws Exception {
        List<Map<String, Object>> batch = IntStream.range(0, events).mapToObj(i -> Map.<String, Object>of("name", "tab_view",
            "props", Map.of("tab", "map"))).toList();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/events"))
            .header("Content-Type", "application/json").header("User-Agent", "Mozilla/5.0 (iPhone) Mobile")
            .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(Map.of("visitorId", visitorId, "events", batch))));
        if (token != null) request.header("X-Explorer-Token", token);
        return send(request.build()).statusCode();
    }

    private List<Integer> 동시에(int count, Callable<Integer> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            futures.add(POOL.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) statuses.add(future.get());
        return statuses;
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    // ---- 동시 수집 ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("같은 방문의 묶음이 동시에 와도 방문은 한 번만 처음 본 것으로 적히고 이벤트는 모두 탐험가 한 사람으로 적힌다")
    void sameVisitorConcurrently() throws Exception {
        String token = 탐험가_토큰();
        String visitor = "visitor-" + UUID.randomUUID();

        List<Integer> statuses = 동시에(12, () -> 보낸다(visitor, token, 2));

        assertThat(statuses).containsOnly(202);
        assertThat(count("SELECT COUNT(*) FROM analytics_visitor WHERE visitor_id = ?", visitor)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM analytics_event WHERE visitor_id = ?", visitor)).isEqualTo(24);
        assertThat(count("SELECT COUNT(DISTINCT actor_key) FROM analytics_event WHERE visitor_id = ?", visitor)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT explorer_hash FROM analytics_visitor WHERE visitor_id = ?", String.class, visitor))
            .hasSize(64);
    }

    @Test
    @DisplayName("여러 방문이 동시에 보내도 모두 적힌다")
    void manyVisitorsConcurrently() throws Exception {
        List<String> visitors = IntStream.range(0, 40).mapToObj(i -> "visitor-many-" + i + "-" + UUID.randomUUID()).toList();
        List<String> queue = new ArrayList<>(visitors);

        List<Integer> statuses = 동시에(40, () -> {
            String visitor;
            synchronized (queue) {
                visitor = queue.remove(0);
            }
            return 보낸다(visitor, null, 3);
        });

        assertThat(statuses).containsOnly(202);
        assertThat(count("SELECT COUNT(*) FROM analytics_visitor")).isEqualTo(40);
        // 다른 시나리오가 만든 탐험가의 가입 사실이 늦게 적힐 수 있어 화면 이벤트만 센다
        assertThat(count("SELECT COUNT(*) FROM analytics_event WHERE source = 'CLIENT'")).isEqualTo(120);
    }

    @Test
    @DisplayName("같은 방문이 동시에 몰려도 몰아서 보낼 수 있는 수만큼만 받는다")
    void rateLimitUnderConcurrency() throws Exception {
        String visitor = "visitor-burst-" + UUID.randomUUID();

        List<Integer> statuses = 동시에(40, () -> 보낸다(visitor, null, 1));

        assertThat(statuses.stream().filter(status -> status == 202).count()).isEqualTo(20);
        assertThat(statuses.stream().filter(status -> status == 429).count()).isEqualTo(20);
        assertThat(count("SELECT COUNT(*) FROM analytics_event WHERE visitor_id = ?", visitor)).isEqualTo(20);
    }

    // ---- 저장 질의 ------------------------------------------------------------------------------------------------

    /**
     * 분석 저장은 JPA 엔티티가 없어 기동 때 스키마 검증(ddl-auto validate)을 받지 않는다 — 대신 저장·집계 질의 상수 전부를 Flyway 로 만든
     * MySQL 에서 EXPLAIN 해 표·열 이름과 문법(MySQL 전용 upsert 포함)을 확인한다(값 자리는 NULL).
     */
    @Test
    @DisplayName("분석이 쓰는 모든 저장·집계 질의가 운영 데이터베이스의 표·열과 맞는다")
    void everyQueryMatchesSchema() throws Exception {
        List<Class<?>> owners = List.of(TrackedEventRow.class, VisitorRow.class, ExplorerJourneyRow.class, DailyMetricRow.class,
            DailyBreakdownRow.class, CohortRow.class, JdbcMetricsReader.class);
        List<String> checked = new ArrayList<>();
        for (Class<?> owner : owners) {
            for (Field field : owner.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;
                field.setAccessible(true);
                String sql = (String) field.get(null);
                if (!sql.matches("(?s)^(SELECT|INSERT|UPDATE|DELETE) .*")) continue;
                String runnable = sql.replaceAll(":[a-zA-Z]+", "NULL").replace("?", "NULL");
                jdbc.queryForList("EXPLAIN " + runnable);
                checked.add(owner.getSimpleName() + "." + field.getName());
            }
        }
        System.out.println("[분석 질의 EXPLAIN] " + checked.size() + "개: " + checked);
        assertThat(checked).hasSizeGreaterThanOrEqualTo(30);
    }

    // ---- 대량 ---------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("원본이 수십만 줄 쌓여도 일 배치와 지표 조회가 정해진 시간 안에 끝나고, 90일 지난 원본은 지워진다")
    void bulk() throws Exception {
        LocalDate today = LocalDate.now(clock);
        int explorers = 6_000;
        int recentEvents = bulkInsert(today, explorers);
        int oldEvents = 30_000;
        insertOld(today.minusDays(120), oldEvents);
        int total = count("SELECT COUNT(*) FROM analytics_event WHERE source = 'CLIENT'");
        assertThat(total).isEqualTo(recentEvents + oldEvents);

        long batchStarted = System.nanoTime();
        HttpResponse<String> batch = send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/admin/metrics/batch"))
            .header("X-Admin-Token", ADMIN_TOKEN).POST(HttpRequest.BodyPublishers.noBody()).build());
        long batchMs = (System.nanoTime() - batchStarted) / 1_000_000;
        long queryStarted = System.nanoTime();
        HttpResponse<String> metrics = send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/admin/metrics?days=35"))
            .header("X-Admin-Token", ADMIN_TOKEN).GET().build());
        long queryMs = (System.nanoTime() - queryStarted) / 1_000_000;
        long nightlyStarted = System.nanoTime();
        HttpResponse<String> nightly = send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/admin/metrics/batch"))
            .header("X-Admin-Token", ADMIN_TOKEN).POST(HttpRequest.BodyPublishers.noBody()).build());
        long nightlyMs = (System.nanoTime() - nightlyStarted) / 1_000_000;
        System.out.printf("[분석 성능] 원본 %d줄(최근 %d + 90일 지난 %d), 탐험가 %d — 첫 일 배치(하루 지표 90일 + 코호트 90일 + 삭제) %dms, "
            + "다음 날부터의 일 배치(하루 지표 3일 + 코호트 35일) %dms, 지표 조회(35일 + 오늘 실시간) %dms%n",
            total, recentEvents, oldEvents, explorers, batchMs, nightlyMs, queryMs);
        assertThat(om.readTree(nightly.body()).get("dailyDays").asInt()).isEqualTo(3);
        assertThat(nightlyMs).isLessThan(30_000);

        assertThat(batch.statusCode()).isEqualTo(200);
        assertThat(om.readTree(batch.body()).get("purged").asInt()).isEqualTo(oldEvents);
        assertThat(count("SELECT COUNT(*) FROM analytics_event WHERE source = 'CLIENT'")).isEqualTo(recentEvents);
        assertThat(metrics.statusCode()).isEqualTo(200);
        JsonNode body = om.readTree(metrics.body());
        assertThat(body.get("missingDays")).isEmpty();
        assertThat(body.get("today").get("mau").asInt()).isPositive();
        assertThat(batchMs).isLessThan(120_000);
        assertThat(queryMs).isLessThan(10_000);
    }

    /** 지난 35일 동안 탐험가 explorers 명의 방문·가입·체크인·화면 이벤트를 여러 줄 INSERT 로 넣는다. @return 넣은 이벤트 수 */
    private int bulkInsert(LocalDate today, int explorers) {
        ExplorerHasher hasher = new ExplorerHasher("bulk-test");
        Random random = new Random(7);
        List<String> visitorRows = new ArrayList<>();
        List<String> explorerRows = new ArrayList<>();
        List<String> eventRows = new ArrayList<>();
        int events = 0;
        for (int i = 0; i < explorers; i++) {
            LocalDate joined = today.minusDays(random.nextInt(35));
            String hash = hasher.hash("bulk-" + i).value();
            String visitor = "bulk-visitor-" + i;
            visitorRows.add(String.format("('%s','%s 03:00:00','%s','%s','%s','MOBILE')", visitor, joined, joined,
                random.nextInt(10) == 0 ? "card" : "direct", hash));
            explorerRows.add(String.format("('%s','%s','%s','%s',%s)", hash, joined, joined, joined.plusDays(7),
                random.nextInt(20) == 0 ? "TRUE" : "FALSE"));
            for (LocalDate day = joined; !day.isAfter(today); day = day.plusDays(1)) {
                if (!day.equals(joined) && random.nextDouble() > 0.35) continue;
                int perDay = 3 + random.nextInt(8);
                for (int j = 0; j < perDay; j++) {
                    String name = j == 0 ? "check_in" : (j == 1 ? "app_open" : (random.nextInt(15) == 0 ? "error_toast" : "tab_view"));
                    String label = switch (name) {
                        case "check_in" -> "common";
                        case "app_open" -> "direct";
                        case "error_toast" -> "DAILY_CAP_EXCEEDED";
                        default -> "map";
                    };
                    eventRows.add(String.format("('%s','CLIENT','%s 05:%02d:00','%s','%s','%s','%s','MOBILE','%s','{}')", name, day,
                        j, day, hash, hash, visitor, label));
                    events++;
                }
            }
        }
        insertRows("INSERT INTO analytics_visitor (visitor_id, first_seen_at, first_seen_day, entry, explorer_hash, device) VALUES ",
            visitorRows);
        insertRows("INSERT INTO analytics_explorer (explorer_hash, created_day, first_check_in_day, revisit_deadline, invite_acquired) "
            + "VALUES ", explorerRows);
        insertRows("INSERT INTO analytics_event (name, source, occurred_at, event_day, actor_key, explorer_hash, visitor_id, device, label, "
            + "props) VALUES ", eventRows);
        return events;
    }

    private void insertOld(LocalDate day, int rows) {
        insertRows("INSERT INTO analytics_event (name, source, occurred_at, event_day, actor_key, device, props) VALUES ",
            IntStream.range(0, rows).mapToObj(i -> String.format("('tab_view','CLIENT','%s 03:00:00','%s','v:old-%d','MOBILE','{}')",
                day, day, i % 500)).toList());
    }

    private void insertRows(String prefix, List<String> rows) {
        for (int from = 0; from < rows.size(); from += 2_000) {
            jdbc.update(prefix + rows.subList(from, Math.min(rows.size(), from + 2_000)).stream().collect(Collectors.joining(",")));
        }
    }
}
