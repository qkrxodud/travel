package com.kobi.territory.account;

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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
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
 * 4단계 병합 경합 MySQL(8.4, REPEATABLE READ) 회귀 — 실제 HTTP.
 * <ul>
 *   <li>같은 계정으로 두 기기가 동시에 처음 로그인: 계정은 하나, 한 기기는 연결·다른 기기는 그 계정으로 병합, 영토는 합쳐진다.</li>
 *   <li>병합 중 체크인(개인 지도·공유 지도): 201 을 받은 체크인은 하나도 잃지 않는다 — 개인 지도 방문은 계정 영토로 옮겨지고,
 *       공유 지도 방문은 탈퇴 처리로 숨겨진다. 병합 뒤 체크인은 401/404 로 거절된다. 5xx·교착 없음.</li>
 * </ul>
 * Docker 가 없으면 skip(CI 는 DockerAvailabilityTest 가 막는다).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.h2.console.enabled=false",
    "spring.datasource.hikari.maximum-pool-size=20"
})
@Import(IntegrationTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountMergeMySqlTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private static final ExecutorService POOL = Executors.newFixedThreadPool(12);
    private static final Duration WAIT = Duration.ofSeconds(60);

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterAll
    static void shutdown() {
        POOL.shutdownNow();
    }

    record Device(String id, String token, String personalMapId) {}

    record Res(int status, String code, JsonNode body) {}

    private Res send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json");
        if (token != null) builder.header("X-Explorer-Token", token);
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = response.body().isBlank() ? om.nullNode() : om.readTree(response.body());
        return new Res(response.statusCode(), response.statusCode() < 300 ? null : json.path("code").asText(), json);
    }

    private Device device() throws Exception {
        JsonNode body = send("POST", "/explorers", null, null).body();
        return new Device(body.get("explorerId").asText(), body.get("accessToken").asText(), body.get("personalMapId").asText());
    }

    private Res checkIn(Device device, String mapId, String code) throws Exception {
        String map = mapId == null ? "" : ",\"mapId\":\"" + mapId + "\"";
        return send("POST", "/visits", device.token(), "{\"regionCode\":\"" + code + "\",\"visitDate\":\""
            + LocalDate.now(clock) + "\"" + map + "}");
    }

    private Res login(String email, Device device) throws Exception {
        return send("POST", "/dev/login", device == null ? null : device.token(), "{\"email\":\"" + email + "\"}");
    }

    /** 모든 작업을 래치로 동시에 출발시킨다. */
    private List<Res> concurrently(List<Callable<Res>> tasks) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Res>> futures = new ArrayList<>();
        for (Callable<Res> task : tasks) futures.add(POOL.submit(() -> { go.await(); return task.call(); }));
        go.countDown();
        List<Res> out = new ArrayList<>();
        for (Future<Res> future : futures) out.add(future.get());
        return out;
    }

    private void awaitQuiet() {
        Awaitility.await().atMost(WAIT).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recalculation_request", Integer.class)).isZero();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery WHERE status = 'FAILED'", Integer.class)).isZero();
    }

    private List<String> visibleRegions(String mapId, String explorerId) {
        return jdbc.queryForList("SELECT region_code FROM visit WHERE map_id = ? AND checked_in_by = ? AND hidden_at IS NULL "
            + "ORDER BY region_code", String.class, mapId, explorerId);
    }

    private static String email() {
        return "mysql" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    @RepeatedTest(5)
    void 같은_계정으로_두_기기가_동시에_처음_로그인해도_계정은_하나_영토는_합쳐진다() throws Exception {
        Device first = device();
        Device second = device();
        assertThat(checkIn(first, null, "KR-11010").status()).isEqualTo(201);
        assertThat(checkIn(second, null, "KR-26010").status()).isEqualTo(201);
        awaitQuiet();
        String email = email();

        List<Res> results = concurrently(List.of(() -> login(email, first), () -> login(email, second)));

        assertThat(results).allSatisfy(result -> assertThat(result.status()).isEqualTo(200));
        assertThat(results.stream().map(result -> result.body().get("outcome").asText()).collect(Collectors.toSet()))
            .containsExactlyInAnyOrder("LINKED", "MERGED");
        String accountId = results.get(0).body().get("explorerId").asText();
        assertThat(results.get(1).body().get("explorerId").asText()).isEqualTo(accountId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account WHERE subject = ?", Integer.class, "dev:" + email))
            .isEqualTo(1);
        awaitQuiet();
        String accountMap = accountId.equals(first.id()) ? first.personalMapId() : second.personalMapId();
        assertThat(visibleRegions(accountMap, accountId)).containsExactly("KR-11010", "KR-26010");
        // 진행도 합쳐졌다(재계산 예약): 지역 2곳의 기본 XP 장부
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id LIKE 'region:%'",
            Integer.class, accountId)).isEqualTo(2);
    }

    @RepeatedTest(8)
    void 병합_중_체크인은_잃지_않는다_개인_지도는_옮겨지고_공유_지도는_재귀속된다() throws Exception {
        String email = email();
        Device account = device();
        assertThat(login(email, account).status()).isEqualTo(200); // 계정 탐험가(연결)
        Device device = device();
        Device owner = device();
        JsonNode map = send("POST", "/maps", owner.token(), "{\"name\":\"경합 원정대\"}").body();
        String sharedMapId = map.get("mapId").asText();
        assertThat(send("POST", "/maps/join", device.token(), "{\"inviteCode\":\"" + map.get("inviteCode").asText() + "\"}")
            .status()).isEqualTo(200);
        assertThat(checkIn(device, null, "KR-11010").status()).isEqualTo(201);
        awaitQuiet();

        List<String> personal = List.of("KR-11020", "KR-11030", "KR-11040", "KR-11050");
        List<String> shared = List.of("KR-26010", "KR-26020", "KR-26030");
        List<Callable<Res>> tasks = new ArrayList<>();
        tasks.add(() -> login(email, device));
        personal.forEach(code -> tasks.add(() -> checkIn(device, null, code)));
        shared.forEach(code -> tasks.add(() -> checkIn(device, sharedMapId, code)));
        List<Res> results = concurrently(tasks);

        Res merged = results.get(0);
        assertThat(merged.status()).isEqualTo(200);
        assertThat(merged.body().get("outcome").asText()).isEqualTo("MERGED");
        List<Res> checkIns = results.subList(1, results.size());
        assertThat(checkIns).allSatisfy(result -> assertThat(result.status()).as(String.valueOf(result.body()))
            .isIn(201, 401, 404));
        awaitQuiet();

        List<String> savedPersonal = new ArrayList<>();
        for (int i = 0; i < personal.size(); i++) if (checkIns.get(i).status() == 201) savedPersonal.add(personal.get(i));
        // 201 을 받은 개인 지도 체크인 + 원래 방문은 모두 계정 영토로
        List<String> expected = new ArrayList<>(savedPersonal);
        expected.add("KR-11010");
        assertThat(visibleRegions(account.personalMapId(), account.id())).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ?", Integer.class, device.personalMapId()))
            .isZero();
        // 공유 지도(Q2): 병합된 탐험가의 방문은 없고, 201 받은 공유 지도 체크인은 모두 계정 탐험가 것으로(재귀속), 계정이 자리를 이었다
        List<String> savedShared = new ArrayList<>();
        for (int i = 0; i < shared.size(); i++) if (checkIns.get(personal.size() + i).status() == 201) savedShared.add(shared.get(i));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ? AND checked_in_by = ?", Integer.class,
            sharedMapId, device.id())).isZero();
        assertThat(visibleRegions(sharedMapId, account.id())).containsExactlyInAnyOrderElementsOf(savedShared);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM map_member WHERE map_id = ? AND explorer_id = ? AND left_at IS NULL",
            Integer.class, sharedMapId, account.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM map_member WHERE map_id = ? AND left_at IS NULL", Integer.class,
            sharedMapId)).isEqualTo(2);
        // 진행: 계정 탐험가의 기본 XP 장부 = 개인 지도 + 재귀속된 공유 지도 방문의 지역 수, 선점 보너스도 계정 기준
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id LIKE 'region:%' "
            + "AND ref_id NOT LIKE '%:revoke'", Integer.class, account.id())).isEqualTo(expected.size() + savedShared.size());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id LIKE ?", Integer.class,
            account.id(), "claim:" + sharedMapId + ":%")).isEqualTo(savedShared.size());
        Map<Integer, Long> statuses = checkIns.stream().collect(Collectors.groupingBy(Res::status, Collectors.counting()));
        assertThat(statuses.keySet()).isSubsetOf(201, 401, 404);
    }

    @RepeatedTest(5)
    void 병합_중_그_지도의_다른_멤버가_체크인해도_선점은_계정으로_가고_남에게_넘어가지_않는다() throws Exception {
        String email = email();
        Device account = device();
        assertThat(login(email, account).status()).isEqualTo(200);
        Device device = device();
        Device owner = device();
        JsonNode map = send("POST", "/maps", owner.token(), "{\"name\":\"선점 경합\"}").body();
        String sharedMapId = map.get("mapId").asText();
        assertThat(send("POST", "/maps/join", device.token(), "{\"inviteCode\":\"" + map.get("inviteCode").asText() + "\"}")
            .status()).isEqualTo(200);
        List<String> claimed = List.of("KR-26010", "KR-26020", "KR-26030");
        for (String code : claimed) assertThat(checkIn(device, sharedMapId, code).status()).isEqualTo(201); // 익명이 선점
        awaitQuiet();
        long transfersBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%ClaimTransferred'", Long.class, sharedMapId);
        clock.advance(Duration.ofSeconds(1)); // 지도장 체크인은 익명보다 나중 처리 시각(가변 시계는 저절로 흐르지 않는다)

        List<String> fresh = List.of("KR-11060", "KR-11070", "KR-11080");
        List<Callable<Res>> tasks = new ArrayList<>();
        tasks.add(() -> login(email, device));
        claimed.forEach(code -> tasks.add(() -> checkIn(owner, sharedMapId, code))); // 같은 지역을 지도장도
        fresh.forEach(code -> tasks.add(() -> checkIn(owner, sharedMapId, code)));
        List<Res> results = concurrently(tasks);
        assertThat(results.get(0).body().get("outcome").asText()).isEqualTo("MERGED");
        assertThat(results.subList(1, results.size())).allSatisfy(result -> assertThat(result.status())
            .as(String.valueOf(result.body())).isEqualTo(201));
        awaitQuiet();

        // 선점: 익명이 먼저 칠한 3곳은 계정이(순서 유지), 새 3곳은 지도장. 선점 이전 이벤트 없음, 지도장 선점 보너스는 새 3곳만
        for (String code : claimed) {
            assertThat(jdbc.queryForObject("SELECT checked_in_by FROM visit WHERE map_id = ? AND region_code = ? AND hidden_at IS NULL "
                + "ORDER BY claim_rank_at, visited_at LIMIT 1", String.class, sharedMapId, code)).as(code).isEqualTo(account.id());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%ClaimTransferred'",
            Long.class, sharedMapId)).isEqualTo(transfersBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id LIKE ?", Integer.class,
            owner.id(), "claim:" + sharedMapId + ":%")).isEqualTo(fresh.size());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id LIKE ?", Integer.class,
            account.id(), "claim:" + sharedMapId + ":%")).isEqualTo(claimed.size());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ?", Integer.class, sharedMapId))
            .isEqualTo(claimed.size() * 2 + fresh.size());
    }
}
