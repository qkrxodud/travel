package com.kobi.territory.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.application.DeliveryPlanningService;
import com.kobi.territory.notification.domain.campaign.Campaign;
import com.kobi.territory.notification.domain.campaign.CampaignMessages;
import com.kobi.territory.notification.domain.campaign.SeasonStart;
import com.kobi.territory.notification.domain.campaign.StreakFacts;
import com.kobi.territory.notification.domain.delivery.PlanDecision;
import com.kobi.territory.notification.domain.policy.NotificationKind;
import com.kobi.territory.notification.domain.push.PushMessage;
import com.kobi.territory.support.IntegrationTestConfig;
import com.kobi.territory.support.MutableClock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 12단계 웹 푸시에 요청이 몰릴 때 — MySQL(기본 REPEATABLE READ) + 실제 HTTP(잠금·격리 규칙의 회귀 테스트). Docker 가 없으면 건너뛴다.
 * 기기 수 상한 경계에서 동시 구독, 같은 브라우저 동시 구독, 같은 사람에게 서로 다른 알림의 동시 계획(하루 한 개), 실제 암호화·서명으로 보내기
 * (local 개발용 가짜 푸시 서비스 {@code /dev/push/inbox}).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.h2.console.enabled=false",
    "spring.datasource.hikari.maximum-pool-size=30"
})
@Import(IntegrationTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("웹 푸시에 요청이 몰릴 때")
class PushMySqlConcurrencyTest {

    static final String REPEAT = "{displayName} — {currentRepetition}/{totalRepetitions}회째";
    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate 월요일 = LocalDate.of(2026, 10, 5);

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private static final ExecutorService POOL = Executors.newFixedThreadPool(16);

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired DeliveryPlanningService planning;

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
        Awaitility.await().atMost(WAIT).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE published_at IS NULL",
            Integer.class) == 0);
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

    /** 이 서버의 개발용 가짜 푸시 서비스 상자로 구독하는 새 브라우저(구독 JSON). */
    private String 브라우저(String box) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        byte[] encoded = generator.generateKeyPair().getPublic().getEncoded();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        return "{\"endpoint\":\"http://localhost:" + port + "/dev/push/inbox/" + box + "\",\"keys\":{\"p256dh\":\""
            + url.encodeToString(Arrays.copyOfRange(encoded, encoded.length - 65, encoded.length)) + "\",\"auth\":\""
            + url.encodeToString(auth) + "\"}}";
    }

    private HttpResponse<String> 구독한다(String explorerId, String subscription) throws Exception {
        return send("POST", "/push/subscriptions", explorerId, subscription);
    }

    private int 기기_수(String explorerId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM push_device WHERE explorer_id = ?", Integer.class, explorerId);
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

    private Instant 서울(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(SEOUL).toInstant();
    }

    // ---- 이야기 ------------------------------------------------------------------------------------------------

    @RepeatedTest(value = 3, name = REPEAT)
    @DisplayName("처음 알림을 켠 탐험가가 열두 기기에서 한꺼번에 구독해도 기기는 다섯 대까지만 남는다")
    void deviceCapUnderConcurrency() throws Exception {
        String me = 탐험가();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String subscription = 브라우저("cap-" + UUID.randomUUID());
            tasks.add(() -> 구독한다(me, subscription).statusCode());
        }

        List<Integer> statuses = 동시에(tasks);

        assertThat(statuses).containsOnly(200);
        assertThat(기기_수(me)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_recipient WHERE explorer_id = ?", Integer.class, me)).isEqualTo(1);
    }

    @RepeatedTest(value = 3, name = REPEAT)
    @DisplayName("같은 브라우저 구독이 한꺼번에 여러 번 와도 기기는 하나다")
    void sameBrowserConcurrently() throws Exception {
        String me = 탐험가();
        String subscription = 브라우저("same-" + UUID.randomUUID());
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) tasks.add(() -> 구독한다(me, subscription).statusCode());

        List<Integer> statuses = 동시에(tasks);

        assertThat(statuses).containsOnly(200);
        assertThat(기기_수(me)).isEqualTo(1);
    }

    @RepeatedTest(value = 3, name = REPEAT)
    @DisplayName("같은 사람에게 미스터리·스트릭·계절 알림이 한꺼번에 계획돼도 그날 받는 알림은 하나다")
    void onePerDayUnderConcurrency() throws Exception {
        String me = 탐험가();
        assertThat(구독한다(me, 브라우저("daily-" + UUID.randomUUID())).statusCode()).isEqualTo(200);
        ExplorerId explorer = ExplorerId.of(me);
        Instant due = 서울(월요일, 9);
        Map<Campaign, PushMessage> campaigns = Map.of(
            new Campaign(NotificationKind.WEEKLY_MYSTERY, "2026-10-05", 월요일, due, false), CampaignMessages.weeklyMystery(월요일),
            new Campaign(NotificationKind.STREAK_GUARD, "2026-10", 월요일, due, false),
            CampaignMessages.streakGuard(new StreakFacts(2, 1, 1), 27, "2026-10"),
            new Campaign(NotificationKind.SEASON_START, "autumn-2026", 월요일, due, false),
            CampaignMessages.seasonStart(new SeasonStart("autumn", "단풍 명소", "🍁", MonthDay.of(10, 1), MonthDay.of(11, 30)), "autumn-2026"));
        List<Callable<PlanDecision>> tasks = new ArrayList<>();
        campaigns.forEach((campaign, message) -> {
            for (int i = 0; i < 4; i++) tasks.add(() -> {
                try {
                    return planning.planFor(explorer, campaign, message);
                } catch (DataIntegrityViolationException plannedConcurrently) {
                    return PlanDecision.ALREADY_PLANNED;
                }
            });
        });

        List<PlanDecision> decisions = 동시에(tasks);

        assertThat(decisions).filteredOn(decision -> decision == PlanDecision.PLANNED).hasSize(1);
        assertThat(decisions).containsOnly(PlanDecision.PLANNED, PlanDecision.DAILY_LIMIT, PlanDecision.ALREADY_PLANNED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_delivery WHERE explorer_id = ? AND delivery_day = ?", Integer.class, me,
            월요일)).isEqualTo(1);
    }

    @Test
    @DisplayName("실제로 암호화하고 서버 신원을 밝혀 브라우저 알림 서비스에 보낸다")
    void endToEnd() throws Exception {
        clock.set(서울(월요일, 9));
        String me = 탐험가();
        String box = "e2e-" + UUID.randomUUID();
        assertThat(구독한다(me, 브라우저(box)).statusCode()).isEqualTo(200);

        HttpResponse<String> sent = send("POST", "/dev/push/send", null, "{\"kind\":\"mystery\"}");

        assertThat(sent.statusCode()).isEqualTo(200);
        Awaitility.await().atMost(WAIT).until(() -> om.readTree(send("GET", "/dev/push/inbox/" + box, null, null).body()).size() == 1);
        JsonNode receipt = om.readTree(send("GET", "/dev/push/inbox/" + box, null, null).body()).get(0);
        assertThat(receipt.get("contentEncoding").asText()).isEqualTo("aes128gcm");
        assertThat(receipt.get("vapid").asBoolean()).isTrue();
        assertThat(receipt.get("topic").asText()).isEqualTo("mystery");
        assertThat(receipt.get("bytes").asInt()).isGreaterThan(86);
        Awaitility.await().atMost(WAIT).until(() -> "SENT".equals(jdbc.queryForObject(
            "SELECT status FROM push_delivery WHERE explorer_id = ?", String.class, me)));
    }

    @Test
    @DisplayName("구독이 없어졌다고 하면 그 기기를 지운다")
    void goneCleanup() throws Exception {
        clock.set(서울(월요일, 10));
        String me = 탐험가();
        assertThat(구독한다(me, 브라우저("gone-" + UUID.randomUUID())).statusCode()).isEqualTo(200);

        send("POST", "/dev/push/send", null, "{\"kind\":\"season\"}");

        Awaitility.await().atMost(WAIT).until(() -> 기기_수(me) == 0);
    }
}
