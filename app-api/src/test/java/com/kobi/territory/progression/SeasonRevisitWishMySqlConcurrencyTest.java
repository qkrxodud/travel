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
 * 9단계 가고 싶은 곳·재방문 도장에 요청이 몰릴 때 — MySQL(기본 REPEATABLE READ) + 실제 HTTP(잠금·격리 규칙의 회귀 테스트). Docker 가 없으면
 * 건너뛴다. 핀 상한 경계에서 동시 꽂기, 새 탐험가의 첫 핀 동시 꽂기, 같은 지역 도장 동시, 도장과 체크인이 하루 상한을 함께 쓸 때.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.h2.console.enabled=false",
    "spring.datasource.hikari.maximum-pool-size=30"
})
@Import(IntegrationTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("가고 싶은 곳·재방문 도장에 요청이 몰릴 때")
class SeasonRevisitWishMySqlConcurrencyTest {

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

    private HttpResponse<String> 핀(String explorerId, String code) throws Exception {
        return send("PUT", "/wishlist/" + code, explorerId, null);
    }

    private int 핀_수(String explorerId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM wish_pin WHERE explorer_id = ? AND fulfilled_at IS NULL", Integer.class,
            explorerId);
    }

    private List<String> 지역들(int count) {
        return catalog.activeRegions().stream().map(RegionView::code).limit(count).toList();
    }

    private void 그날로(int year, int month, int day) {
        clock.set(LocalDate.of(year, month, day).atTime(12, 0).atZone(clock.getZone()).toInstant());
    }

    // ---- 이야기 ------------------------------------------------------------------------------------------------

    @RepeatedTest(value = 3, name = REPEAT)
    @DisplayName("핀이 스물아홉 개일 때 여섯 곳을 한꺼번에 꽂아도 서른 개째 하나만 꽂히고 나머지는 가득 찼다고 거절된다")
    void wishlistBoundary() throws Exception {
        String me = 탐험가();
        List<String> regions = 지역들(35);
        for (String code : regions.subList(0, 29)) assertThat(핀(me, code).statusCode()).isEqualTo(200);

        List<Callable<HttpResponse<String>>> tasks = new ArrayList<>();
        regions.subList(29, 35).forEach(code -> tasks.add(() -> 핀(me, code)));
        List<HttpResponse<String>> results = 동시에(tasks);

        assertThat(results).filteredOn(response -> response.statusCode() == 200).hasSize(1);
        assertThat(results).filteredOn(response -> response.statusCode() == 422)
            .hasSize(5).allSatisfy(response -> assertThat(response.body()).contains("WISHLIST_FULL"));
        assertThat(핀_수(me)).isEqualTo(30);
    }

    @RepeatedTest(value = 3, name = REPEAT)
    @DisplayName("새 탐험가가 첫 핀 여섯 곳을 한꺼번에 꽂아도 모두 꽂힌다")
    void firstPinsTogether() throws Exception {
        String me = 탐험가();
        List<Callable<Integer>> tasks = new ArrayList<>();
        지역들(6).forEach(code -> tasks.add(() -> 핀(me, code).statusCode()));

        assertThat(동시에(tasks)).containsOnly(200);
        assertThat(핀_수(me)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wishlist WHERE explorer_id = ?", Integer.class, me)).isEqualTo(1);
    }

    @RepeatedTest(value = 3, name = REPEAT)
    @DisplayName("다음 해에 같은 지역 도장을 여덟 번 한꺼번에 눌러도 도장·XP·색 변형·소식은 하나씩이다")
    void sameStampTogether() throws Exception {
        그날로(2026, 10, 2);
        String me = 탐험가();
        assertThat(칠한다(me, "KR-11010").statusCode()).isEqualTo(201);
        그날로(2027, 3, 2);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) tasks.add(() -> send("POST", "/revisits/KR-11010", me, null).statusCode());
        List<Integer> results = 동시에(tasks);

        assertThat(results).filteredOn(code -> code == 201).hasSize(1);
        assertThat(results).filteredOn(code -> code == 409).hasSize(7);
        모두_전달될_때까지();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM revisit_stamp WHERE explorer_id = ?", Integer.class, me)).isEqualTo(1);
        assertThat(장부(me, "revisit:" + me + ":KR-11010@2027")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_revisit WHERE explorer_id = ?", Integer.class, me)).isEqualTo(1);
    }

    @RepeatedTest(value = 3, name = REPEAT)
    @DisplayName("도장 네 번과 새 체크인 네 번을 한꺼번에 해도 하루 상한 다섯 건까지만 받아들인다")
    void stampsAndCheckInsShareCap() throws Exception {
        그날로(2026, 10, 2);
        String me = 탐험가();
        List<String> regions = 시도의_지역("KR-11");
        for (String code : regions.subList(0, 4)) assertThat(칠한다(me, code).statusCode()).isEqualTo(201);
        그날로(2027, 3, 3);

        List<Callable<Integer>> tasks = new ArrayList<>();
        regions.subList(0, 4).forEach(code -> tasks.add(() -> send("POST", "/revisits/" + code, me, null).statusCode()));
        regions.subList(4, 8).forEach(code -> tasks.add(() -> 칠한다(me, code).statusCode()));
        List<Integer> results = 동시에(tasks);

        assertThat(results).filteredOn(code -> code == 201).hasSize(5);
        assertThat(results).filteredOn(code -> code == 422).hasSize(3);
    }
}
