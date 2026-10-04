package com.kobi.territory.notification;

import static com.kobi.territory.support.Explorers.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.notification.application.CampaignRun;
import com.kobi.territory.notification.application.CampaignRunner;
import com.kobi.territory.notification.application.DeliveryDispatcher;
import com.kobi.territory.notification.domain.delivery.PlanDecision;
import com.kobi.territory.support.Explorers;
import com.kobi.territory.support.Explorers.Anonymous;
import com.kobi.territory.support.Explorers.Session;
import com.kobi.territory.support.FakePushService;
import com.kobi.territory.support.FakePushService.Browser;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 12단계 웹 푸시: 구독·해지·설정(API), 알림 3종 계획(달력·동의·설정·하루 한 개·조용한 시간·멱등), 발송(가짜 푸시 서비스에 실제 암호화·서명으로),
 * 구독 만료 정리·재시도, 계정 병합, 분석 push_sent, local 즉시 발송. 가짜 푸시 서비스는 이 JVM 의 임의 포트(local 은 localhost 구독 주소를 받는다).
 */
@IntegrationTest
@DisplayName("웹 푸시 알림")
class PushIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate 월요일 = LocalDate.of(2026, 10, 5);
    private static final FakePushService PUSH = new FakePushService();
    private static final List<String> TABLES = List.of("push_delivery", "push_device", "push_recipient");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired Explorers explorers;
    @Autowired JdbcTemplate jdbc;
    @Autowired CampaignRunner campaigns;
    @Autowired DeliveryDispatcher dispatcher;

    private Instant 처음시각;

    @BeforeEach
    void clean() {
        처음시각 = clock.instant();
        explorers.전달이_끝날_때까지();
        TABLES.forEach(table -> jdbc.update("DELETE FROM " + table));
        jdbc.update("DELETE FROM analytics_event WHERE name = 'push_sent'");
        PUSH.clear();
    }

    @AfterEach
    void restoreClock() {
        explorers.전달이_끝날_때까지();
        clock.set(처음시각);
    }

    @AfterAll
    static void stopPushService() {
        PUSH.close();
    }

    // ---- 준비 문장 ----

    private void 서울_시각(LocalDate day, int hour, int minute) {
        clock.set(LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(SEOUL).toInstant());
    }

    private ResultActions 구독한다(Anonymous who, Browser browser) throws Exception {
        return explorers.기기로(who, post("/push/subscriptions").contentType(MediaType.APPLICATION_JSON).content(browser.subscriptionJson()));
    }

    private Browser 알림을_켠다(Anonymous who, String box) throws Exception {
        Browser browser = PUSH.browser(box);
        구독한다(who, browser).andExpect(status().isOk());
        return browser;
    }

    private void 설정한다(Anonymous who, boolean mystery, boolean streak, boolean season) throws Exception {
        explorers.기기로(who, put("/push/preferences").contentType(MediaType.APPLICATION_JSON)
            .content("{\"mystery\":" + mystery + ",\"streak\":" + streak + ",\"season\":" + season + "}")).andExpect(status().isOk());
    }

    private JsonNode 설정(Anonymous who) throws Exception {
        return explorers.json(explorers.기기로(who, get("/push/preferences")).andExpect(status().isOk()));
    }

    private int 기기_수(String explorerId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM push_device WHERE explorer_id = ?", Integer.class, explorerId);
    }

    private List<String> 발송_상태(String explorerId) {
        return jdbc.queryForList("SELECT status FROM push_delivery WHERE explorer_id = ? ORDER BY id", String.class, explorerId);
    }

    private void 보낸다() {
        dispatcher.dispatchDue();
    }

    private static int 판단(CampaignRun run, PlanDecision decision) {
        return run.decisions().getOrDefault(decision, 0);
    }

    private ResultActions 요청(MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder);
    }

    @Nested
    @DisplayName("이 브라우저로 알림을 받겠다고 하면")
    class Subscribe {

        @Test
        @DisplayName("브라우저가 구독에 쓸 서버 공개 키는 로그인 없이 받는다")
        void publicKey() throws Exception {
            요청(get("/push/vapid-public-key")).andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("BL4nehRk6sf8mdeCUNdZYgFJV8YQKhg_DBB2UPhEBtJhpCGph6jaB7u4cWesO4IFwVwnsate9RFv1cnqDuuXjgU"));
        }

        @Test
        @DisplayName("기기가 생기고, 처음에는 세 종류 알림이 모두 켜져 있으며 조용한 시간과 하루 개수를 알려 준다")
        void subscribed() throws Exception {
            Anonymous 탐험가 = explorers.익명_탐험가();

            구독한다(탐험가, PUSH.browser("phone")).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.devices").value(1));

            JsonNode settings = 설정(탐험가);
            assertThat(settings.get("mystery").asBoolean()).isTrue();
            assertThat(settings.get("streak").asBoolean()).isTrue();
            assertThat(settings.get("season").asBoolean()).isTrue();
            assertThat(settings.get("devices").asInt()).isEqualTo(1);
            assertThat(settings.at("/quietHours/start").asText()).isEqualTo("22:00");
            assertThat(settings.at("/quietHours/end").asText()).isEqualTo("08:00");
            assertThat(settings.get("dailyLimit").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("앱을 열 때마다 같은 구독을 다시 보내도 기기가 늘지 않는다")
        void sameBrowserAgain() throws Exception {
            Anonymous 탐험가 = explorers.익명_탐험가();
            Browser phone = 알림을_켠다(탐험가, "phone");

            구독한다(탐험가, phone).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.devices").value(1));
        }

        @Test
        @DisplayName("여러 기기로 받을 수 있다")
        void manyDevices() throws Exception {
            Anonymous 탐험가 = explorers.익명_탐험가();
            알림을_켠다(탐험가, "phone");
            알림을_켠다(탐험가, "laptop");

            assertThat(기기_수(탐험가.id())).isEqualTo(2);
        }

        @Test
        @DisplayName("같은 브라우저로 다른 탐험가가 구독하면 그 브라우저는 새 탐험가의 기기가 된다")
        void browserMovesToNewExplorer() throws Exception {
            Anonymous 먼저 = explorers.익명_탐험가();
            Anonymous 나중 = explorers.익명_탐험가();
            Browser shared = 알림을_켠다(먼저, "shared");

            구독한다(나중, shared).andExpect(status().isOk());

            assertThat(기기_수(먼저.id())).isZero();
            assertThat(기기_수(나중.id())).isEqualTo(1);
        }

        @Test
        @DisplayName("탐험가를 모르면 구독할 수 없다")
        void requiresExplorer() throws Exception {
            요청(post("/push/subscriptions").contentType(MediaType.APPLICATION_JSON).content(PUSH.browser("x").subscriptionJson()))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("알려진 브라우저 알림 서비스가 아닌 주소나 키가 빠진 구독은 받지 않는다")
        void rejected() throws Exception {
            Anonymous 탐험가 = explorers.익명_탐험가();
            explorers.기기로(탐험가, post("/push/subscriptions").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"endpoint\":\"https://evil.example.com/hook\",\"keys\":{\"p256dh\":\"" + PUSH.browser("x").p256dh()
                        + "\",\"auth\":\"" + PUSH.browser("x").auth() + "\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PUSH_ENDPOINT_NOT_ALLOWED"));
            explorers.기기로(탐험가, post("/push/subscriptions").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/abc\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PUSH_SUBSCRIPTION"));
        }
    }

    @Nested
    @DisplayName("해지하거나 설정을 바꾸면")
    class UnsubscribeAndPreferences {

        @Test
        @DisplayName("해지한 기기로는 더 받지 않고, 다시 해지해도 아무 일 없다")
        void unsubscribe() throws Exception {
            Anonymous 탐험가 = explorers.익명_탐험가();
            Browser phone = 알림을_켠다(탐험가, "phone");
            String body = "{\"endpoint\":\"" + phone.endpoint() + "\"}";

            explorers.기기로(탐험가, delete("/push/subscriptions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
            explorers.기기로(탐험가, delete("/push/subscriptions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

            assertThat(설정(탐험가).get("devices").asInt()).isZero();
        }

        @Test
        @DisplayName("종류별로 끄고 켤 수 있고, 세 가지를 모두 정해서 보내야 한다")
        void preferences() throws Exception {
            Anonymous 탐험가 = explorers.익명_탐험가();
            설정한다(탐험가, true, false, true);

            assertThat(설정(탐험가).get("streak").asBoolean()).isFalse();
            explorers.기기로(탐험가, put("/push/preferences").contentType(MediaType.APPLICATION_JSON).content("{\"mystery\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PUSH_PREFERENCES"));
        }
    }

    @Nested
    @DisplayName("월요일 아침 이번 주 미스터리 지역 알림은")
    class WeeklyMystery {

        @Test
        @DisplayName("알림을 켠 사람의 기기에 암호화해 보내고, 지역 이름은 싣지 않으며, 누르면 지도를 연다")
        void delivered() throws Exception {
            서울_시각(월요일, 9, 0);
            Anonymous 탐험가 = explorers.익명_탐험가();
            Browser phone = 알림을_켠다(탐험가, "mystery-phone");

            CampaignRun run = campaigns.weeklyMystery(false);
            보낸다();

            assertThat(판단(run, PlanDecision.PLANNED)).isEqualTo(1);
            await().atMost(Explorers.WAIT).until(() -> PUSH.received("mystery-phone").size() == 1);
            String payload = FakePushService.decrypt(PUSH.received("mystery-phone").get(0), phone);
            JsonNode message = om.readTree(payload);
            assertThat(message.get("kind").asText()).isEqualTo("mystery");
            assertThat(message.get("url").asText()).isEqualTo("/?from=push&push=mystery#map");
            assertThat(payload).doesNotContain("KR-");
            assertThat(PUSH.received("mystery-phone").get(0).headers().get("authorization")).startsWith("vapid t=");
            await().atMost(Explorers.WAIT).until(() -> 발송_상태(탐험가.id()).equals(List.of("SENT")));
        }

        @Test
        @DisplayName("보냈다는 사실이 분석에 남는다 — 알림 종류만, 누구인지는 해시로")
        void analytics() throws Exception {
            서울_시각(월요일, 9, 0);
            Anonymous 탐험가 = explorers.익명_탐험가();
            알림을_켠다(탐험가, "analytics-phone");

            campaigns.weeklyMystery(false);
            보낸다();

            await().atMost(Explorers.WAIT).until(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM analytics_event WHERE name = 'push_sent' AND label = 'mystery'", Integer.class) == 1);
            assertThat(jdbc.queryForObject("SELECT props FROM analytics_event WHERE name = 'push_sent'", String.class))
                .doesNotContain(탐험가.id());
        }

        @Test
        @DisplayName("같은 주에 스케줄이 다시 돌아도 한 번만 보낸다")
        void oncePerWeek() throws Exception {
            서울_시각(월요일, 9, 0);
            Anonymous 탐험가 = explorers.익명_탐험가();
            알림을_켠다(탐험가, "twice");

            campaigns.weeklyMystery(false);
            CampaignRun again = campaigns.weeklyMystery(false);

            assertThat(판단(again, PlanDecision.ALREADY_PLANNED)).isEqualTo(1);
            assertThat(발송_상태(탐험가.id())).hasSize(1);
        }

        @Test
        @DisplayName("미스터리 알림을 끈 사람과 알림에 동의하지 않은 사람에게는 보내지 않는다")
        void consentAndPreferences() throws Exception {
            서울_시각(월요일, 9, 0);
            Anonymous 끈_사람 = explorers.익명_탐험가();
            알림을_켠다(끈_사람, "off");
            설정한다(끈_사람, false, true, true);
            Anonymous 동의_안_한_사람 = explorers.익명_탐험가();

            CampaignRun run = campaigns.weeklyMystery(false);

            assertThat(run.planned()).isZero();
            assertThat(발송_상태(끈_사람.id())).isEmpty();
            assertThat(발송_상태(동의_안_한_사람.id())).isEmpty();
        }

        @Test
        @DisplayName("계획한 뒤 보내기 전에 끄면 보내지 않는다")
        void turnedOffBeforeSending() throws Exception {
            서울_시각(월요일, 9, 0);
            Anonymous 탐험가 = explorers.익명_탐험가();
            알림을_켠다(탐험가, "changed-mind");
            서울_시각(월요일, 6, 0);
            campaigns.weeklyMystery(false);   // 조용한 시간이라 08:00 에 보낼 계획

            설정한다(탐험가, false, true, true);
            서울_시각(월요일, 8, 0);
            보낸다();

            await().atMost(Explorers.WAIT).until(() -> 발송_상태(탐험가.id()).equals(List.of("CANCELLED")));
            assertThat(PUSH.received("changed-mind")).isEmpty();
        }

        @Test
        @DisplayName("월요일이 아니면 아무에게도 계획하지 않는다")
        void notMonday() throws Exception {
            서울_시각(월요일.plusDays(1), 9, 0);
            알림을_켠다(explorers.익명_탐험가(), "tuesday");

            assertThat(campaigns.weeklyMystery(false).period()).isNull();
        }
    }

    @Nested
    @DisplayName("같은 사람에게는")
    class DailyLimit {

        @Test
        @DisplayName("하루에 알림 하나만 — 계절 테마 시작 알림을 받은 월요일에는 미스터리 알림을 보내지 않는다")
        void onePerDay() throws Exception {
            서울_시각(월요일, 8, 30);
            Anonymous 탐험가 = explorers.익명_탐험가();
            알림을_켠다(탐험가, "one-a-day");

            List<CampaignRun> seasons = campaigns.seasonStarts(true);
            서울_시각(월요일, 9, 0);
            CampaignRun mystery = campaigns.weeklyMystery(false);

            assertThat(판단(seasons.get(0), PlanDecision.PLANNED)).isEqualTo(1);
            assertThat(판단(mystery, PlanDecision.DAILY_LIMIT)).isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT kind FROM push_delivery WHERE explorer_id = ?", String.class, 탐험가.id()))
                .containsExactly("season");
        }

        @Test
        @DisplayName("밤 10시 이후에 계획된 알림은 다음 날 아침 8시에 보낸다 — 밤에는 기다린다")
        void quietHours() throws Exception {
            LocalDate 스트릭_날 = LocalDate.of(2026, 10, 28);
            서울_시각(LocalDate.of(2026, 9, 15), 12, 0);
            Anonymous 탐험가 = explorers.익명_탐험가();
            explorers.칠한다(탐험가, "KR-11010");
            explorers.전달이_끝날_때까지();
            알림을_켠다(탐험가, "night");

            서울_시각(스트릭_날, 23, 0);
            CampaignRun run = campaigns.streakGuard(false);
            서울_시각(스트릭_날, 23, 30);
            보낸다();
            assertThat(PUSH.received("night")).isEmpty();

            서울_시각(스트릭_날.plusDays(1), 8, 0);
            보낸다();

            assertThat(run.dueAt()).isEqualTo(LocalDateTime.of(스트릭_날.plusDays(1), LocalTime.of(8, 0)).atZone(SEOUL).toInstant());
            await().atMost(Explorers.WAIT).until(() -> PUSH.received("night").size() == 1);
        }
    }

    @Nested
    @DisplayName("월말 스트릭 지키기 알림은")
    class StreakGuard {

        @Test
        @DisplayName("지난달까지 이어 왔는데 이번 달 새 지역이 없는 사람에게만, 연속 개월과 보호권을 안내해 보낸다")
        void onlyAtRisk() throws Exception {
            서울_시각(LocalDate.of(2026, 9, 15), 12, 0);
            Anonymous 위험 = explorers.익명_탐험가();
            explorers.칠한다(위험, "KR-11010");
            Anonymous 이번달_칠함 = explorers.익명_탐험가();
            explorers.칠한다(이번달_칠함, "KR-11010");
            Anonymous 처음 = explorers.익명_탐험가();
            서울_시각(LocalDate.of(2026, 10, 10), 12, 0);
            explorers.칠한다(이번달_칠함, "KR-11020");
            explorers.전달이_끝날_때까지();
            Browser phone = 알림을_켠다(위험, "streak-risk");
            알림을_켠다(이번달_칠함, "streak-safe");
            알림을_켠다(처음, "streak-new");

            서울_시각(LocalDate.of(2026, 10, 28), 19, 0);
            CampaignRun run = campaigns.streakGuard(false);
            보낸다();

            assertThat(run.planned()).isEqualTo(1);
            assertThat(run.notTarget()).isEqualTo(2);
            await().atMost(Explorers.WAIT).until(() -> PUSH.received("streak-risk").size() == 1);
            JsonNode message = om.readTree(FakePushService.decrypt(PUSH.received("streak-risk").get(0), phone));
            assertThat(message.get("title").asText()).contains("1개월");
            assertThat(message.get("body").asText()).contains("4일 남았어요").contains("보호권");
            assertThat(PUSH.received("streak-safe")).isEmpty();
        }

        @Test
        @DisplayName("월말 사흘 전이 아니면 계획하지 않는다")
        void otherDays() {
            서울_시각(LocalDate.of(2026, 10, 27), 19, 0);

            assertThat(campaigns.streakGuard(false).period()).isNull();
        }
    }

    @Nested
    @DisplayName("알림 서비스가 받지 못하면")
    class Failures {

        @Test
        @DisplayName("구독이 없어졌다고 하면 그 기기를 지우고 다음부터 보내지 않는다")
        void goneDeviceRemoved() throws Exception {
            서울_시각(월요일, 9, 0);
            Anonymous 탐험가 = explorers.익명_탐험가();
            알림을_켠다(탐험가, "gone-phone");

            campaigns.weeklyMystery(false);
            보낸다();

            await().atMost(Explorers.WAIT).until(() -> 기기_수(탐험가.id()) == 0);
            assertThat(발송_상태(탐험가.id())).containsExactly("CANCELLED");
        }

        @Test
        @DisplayName("잠시 받지 못하면 조금 뒤 다시 보내 받게 한다")
        void retried() throws Exception {
            서울_시각(월요일, 9, 0);
            Anonymous 탐험가 = explorers.익명_탐험가();
            알림을_켠다(탐험가, "busy-phone");
            campaigns.weeklyMystery(false);
            보낸다();
            await().atMost(Explorers.WAIT).until(() -> 발송_상태(탐험가.id()).equals(List.of("PENDING")));

            PUSH.respond("busy-phone", 201);
            clock.advance(Duration.ofMinutes(5));
            보낸다();

            await().atMost(Explorers.WAIT).until(() -> 발송_상태(탐험가.id()).equals(List.of("SENT")));
            assertThat(PUSH.received("busy-phone")).hasSize(2);
        }

        @Test
        @DisplayName("한 기기가 받으면 다른 기기의 구독이 없어졌어도 보낸 것으로 하고 그 기기만 지운다")
        void partial() throws Exception {
            서울_시각(월요일, 9, 0);
            Anonymous 탐험가 = explorers.익명_탐험가();
            알림을_켠다(탐험가, "alive");
            알림을_켠다(탐험가, "gone-old");

            campaigns.weeklyMystery(false);
            보낸다();

            await().atMost(Explorers.WAIT).until(() -> 발송_상태(탐험가.id()).equals(List.of("SENT")) && 기기_수(탐험가.id()) == 1);
        }
    }

    @Nested
    @DisplayName("익명으로 알림을 켠 뒤 계정에 로그인하면")
    class Merge {

        @Test
        @DisplayName("기존 계정에 병합되면서 그 브라우저가 계정의 기기가 된다")
        void devicesFollowMerge() throws Exception {
            String email = Explorers.새_이메일("push");
            Session account = explorers.로그인(null, email);
            Anonymous 익명 = explorers.익명_탐험가();
            알림을_켠다(익명, "merged-phone");

            explorers.로그인(익명, email);
            explorers.전달이_끝날_때까지();

            await().atMost(Explorers.WAIT).until(() -> 기기_수(account.explorerId()) == 1 && 기기_수(익명.id()) == 0);
        }
    }

    @Nested
    @DisplayName("개발용 즉시 발송은")
    class DevSend {

        @Test
        @DisplayName("요일·조용한 시간과 상관없이 지금 계획하고 보낸다 — 동의·설정·하루 한 개는 그대로")
        void now() throws Exception {
            서울_시각(월요일.plusDays(2), 23, 0);
            Anonymous 탐험가 = explorers.익명_탐험가();
            알림을_켠다(탐험가, "dev-phone");

            요청(post("/dev/push/send").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"mystery\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.runs[0].decisions.PLANNED").value(1));

            await().atMost(Explorers.WAIT).until(() -> PUSH.received("dev-phone").size() == 1);
            explorers.기기로(탐험가, get("/dev/push/deliveries")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("mystery")).andExpect(jsonPath("$[0].status").value("SENT"));
            요청(post("/dev/push/send").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"season\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.runs[0].decisions.DAILY_LIMIT").value(1));
        }

        @Test
        @DisplayName("모르는 알림 종류는 받지 않는다")
        void unknownKind() throws Exception {
            요청(post("/dev/push/send").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"promo\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_NOTIFICATION_KIND"));
        }

        @Test
        @DisplayName("스케줄과 똑같이 판단하게 하면 그날이 아닐 때 아무것도 계획하지 않는다")
        void notForced() throws Exception {
            서울_시각(월요일.plusDays(2), 12, 0);
            알림을_켠다(explorers.익명_탐험가(), "dev-not-forced");

            요청(post("/dev/push/send").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"streak\",\"force\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.runs[0].period").doesNotExist());
        }
    }
}
