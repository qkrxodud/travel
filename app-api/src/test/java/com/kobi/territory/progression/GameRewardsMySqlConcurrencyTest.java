package com.kobi.territory.progression;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.support.IntegrationTestConfig;
import com.kobi.territory.support.MutableClock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * 8단계 게임 보상이 동시에 몰린 요청에서도 한 번씩만 주어지는지 — MySQL(기본 REPEATABLE READ) + 실제 HTTP. Docker 가 없으면 건너뛴다.
 * 정복 순간·마일스톤 순간의 동시 체크인, 재계산과 겹친 정복, 새 주 미스터리 지역의 첫 조회 경합.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.h2.console.enabled=false",
    "spring.datasource.hikari.maximum-pool-size=30"
})
@Import(IntegrationTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("게임 보상에 요청이 몰릴 때")
class GameRewardsMySqlConcurrencyTest {

    static final String REPEAT = "{displayName} — {currentRepetition}/{totalRepetitions}회째";
    private static final Duration WAIT = Duration.ofSeconds(30);

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private static final ExecutorService POOL = Executors.newFixedThreadPool(12);

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired RegionCatalog catalog;

    private final HttpClient http = HttpClient.newHttpClient();
    private final Map<String, String> tokens = new ConcurrentHashMap<>();
    private Instant 처음시각;

    @AfterAll
    static void shutdown() {
        POOL.shutdownNow();
    }

    @BeforeEach
    void rememberClock() {
        처음시각 = clock.instant();
    }

    @AfterEach
    void restoreClock() {
        모두_전달될_때까지();
        clock.set(처음시각);
    }

    // ---- 준비 문장 ---------------------------------------------------------------------------------------------

    private HttpResponse<String> send(String method, String path, String explorerId, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json");
        if (explorerId != null) builder.header("X-Explorer-Token", tokens.get(explorerId));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String 탐험가() throws Exception {
        JsonNode explorer = om.readTree(send("POST", "/explorers", null, null).body());
        tokens.put(explorer.get("explorerId").asText(), explorer.get("accessToken").asText());
        return explorer.get("explorerId").asText();
    }

    private HttpResponse<String> 칠한다(String explorerId, String code) throws Exception {
        return send("POST", "/visits", explorerId, "{\"regionCode\":\"" + code + "\",\"visitDate\":\"" + LocalDate.now(clock) + "\"}");
    }

    private List<String> 시도의_지역(String provinceCode) {
        return catalog.activeRegions().stream().filter(region -> region.provinceCode().equals(provinceCode)).map(RegionView::code)
            .toList();
    }

    /** 모든 작업을 래치로 동시에 출발시키고 끝까지 기다린다. */
    private <T> List<T> 동시에(List<Callable<T>> tasks) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        tasks.forEach(task -> futures.add(POOL.submit(() -> {
            go.await();
            return task.call();
        })));
        go.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) results.add(future.get());
        return results;
    }

    private List<Integer> 한꺼번에_칠한다(String explorerId, List<String> codes) throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        codes.forEach(code -> tasks.add(() -> 칠한다(explorerId, code).statusCode()));
        return 동시에(tasks);
    }

    private void 모두_전달될_때까지() {
        Awaitility.await().atMost(WAIT).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery WHERE status <> 'DELIVERED'", Integer.class)).isZero();
        });
    }

    private int 장부(String explorerId, String refId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id = ?", Integer.class, explorerId, refId);
    }

    private int 가방(String explorerId, String itemId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM owned_item WHERE explorer_id = ? AND item_id = ?", Integer.class, explorerId,
            itemId);
    }

    private void 그달로(YearMonth month) {
        clock.set(month.atDay(15).atTime(12, 0).atZone(clock.getZone()).toInstant());
    }

    // ---- 이야기 ------------------------------------------------------------------------------------------------

    @RepeatedTest(value = 3, name = REPEAT)
    @DisplayName("대전 다섯 곳을 한꺼번에 칠해 정복해도 정복 보상·대표 장식·소식은 하나씩이다")
    void conquestOnce() throws Exception {
        String me = 탐험가();

        assertThat(한꺼번에_칠한다(me, 시도의_지역("KR-25"))).containsOnly(201);
        모두_전달될_때까지();

        assertThat(장부(me, "conquest:" + me + ":KR-25")).isEqualTo(1);
        assertThat(가방(me, "conquest:KR-25")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_entry WHERE actor_id = ? AND kind = 'PROVINCE_CONQUERED'",
            Integer.class, me)).isEqualTo(1);
    }

    @Test
    @DisplayName("울산을 한꺼번에 칠하는 동안 다시 세기가 겹쳐도 정복 보상과 대표 장식은 하나씩이다")
    void conquestWithRecalculation() throws Exception {
        String me = 탐험가();
        List<Callable<Integer>> tasks = new ArrayList<>();
        시도의_지역("KR-26").forEach(code -> tasks.add(() -> 칠한다(me, code).statusCode()));
        tasks.add(() -> send("POST", "/dev/recalculate", me, null).statusCode());

        assertThat(동시에(tasks)).containsOnly(200, 201);
        모두_전달될_때까지();
        assertThat(send("POST", "/dev/recalculate", me, null).statusCode()).isEqualTo(200);

        assertThat(장부(me, "conquest:" + me + ":KR-26")).isEqualTo(1);
        assertThat(가방(me, "conquest:KR-26")).isEqualTo(1);
    }

    @Test
    @DisplayName("세 번째 달에 서울 다섯 곳을 한꺼번에 칠해도 마일스톤 보상·보호권·한정 아이템은 하나씩이다")
    void milestoneOnce() throws Exception {
        String me = 탐험가();
        YearMonth first = YearMonth.from(clock.instant().atZone(clock.getZone()));
        List<String> seoul = 시도의_지역("KR-11");
        assertThat(칠한다(me, seoul.get(0)).statusCode()).isEqualTo(201);
        그달로(first.plusMonths(1));
        assertThat(칠한다(me, seoul.get(1)).statusCode()).isEqualTo(201);
        모두_전달될_때까지();
        그달로(first.plusMonths(2));

        assertThat(한꺼번에_칠한다(me, seoul.subList(2, 7))).containsOnly(201);
        모두_전달될_때까지();

        assertThat(장부(me, "milestone:" + me + ":3")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM streak_freeze WHERE explorer_id = ?", Integer.class, me))
            .isEqualTo(1);
        assertThat(가방(me, "streak:3")).isEqualTo(1);
    }

    @Test
    @DisplayName("새 주의 미스터리 지역을 열두 명이 동시에 처음 물어도 모두 같은 지역을 받고 기록은 하나다")
    void mysteryFirstLookup() throws Exception {
        clock.set(clock.instant().plus(Duration.ofDays(7L * 40))); // 아직 아무도 묻지 않은 주
        LocalDate weekStart = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<String> explorers = new ArrayList<>();
        for (int count = 0; count < 12; count++) explorers.add(탐험가());
        List<Callable<String>> tasks = new ArrayList<>();
        explorers.forEach(explorer -> tasks.add(() ->
            om.readTree(send("GET", "/mystery/this-week", explorer, null).body()).get("region").get("code").asText()));

        Set<String> regions = new HashSet<>(동시에(tasks));

        assertThat(regions).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mystery_week WHERE week_start = ?", Integer.class, weekStart))
            .isEqualTo(1);
    }
}
