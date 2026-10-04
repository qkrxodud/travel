package com.kobi.territory.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.analytics.application.AnalyticsSubscriptions;
import com.kobi.territory.common.event.EventSubscriber;
import com.kobi.territory.exploration.api.event.MemberJoined;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.support.Explorers;
import com.kobi.territory.support.Explorers.Anonymous;
import com.kobi.territory.support.Explorers.Session;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.IntegrationTestConfig.CapturedEvents;
import com.kobi.territory.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 10단계 분석 D2·D3·D4: 화면 이벤트 받기(검증·개인정보 차단·크기·레이트 리밋·탐험가 연결), 서버 사실 기록(멱등·첫 체크인·초대 경로·계정 연결),
 * 공개 페이지 열람, 운영 지표(인증·퍼널·리텐션·K 계수·기능별 사용률·오류 코드·일 배치·90일 삭제), local 시드.
 */
@IntegrationTest
@DisplayName("분석 — 이벤트 수집과 운영 지표")
class AnalyticsIntegrationTest {

    private static final String ADMIN = "X-Admin-Token";
    private static final String ADMIN_TOKEN = "local-admin-token";
    private static final String 종로구 = "KR-11010";
    private static final String 중구 = "KR-11020";
    private static final List<String> TABLES = List.of("analytics_event", "analytics_visitor", "analytics_explorer",
        "analytics_daily_breakdown", "analytics_daily", "analytics_cohort");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired Explorers explorers;
    @Autowired JdbcTemplate jdbc;
    @Autowired List<EventSubscriber> subscribers;
    @Autowired CapturedEvents captured;

    private Instant 처음시각;

    @BeforeEach
    void cleanAnalytics() {
        처음시각 = clock.instant();
        explorers.전달이_끝날_때까지();
        TABLES.forEach(table -> jdbc.update("DELETE FROM " + table));
    }

    @AfterEach
    void restoreClock() {
        explorers.전달이_끝날_때까지();
        clock.set(처음시각);
    }

    // ---- 준비 문장 ---------------------------------------------------------------------------------------------

    private static String 새_방문() {
        return "visitor-" + UUID.randomUUID();
    }

    private static Map<String, Object> 이벤트(String name, Object... keyValues) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) props.put((String) keyValues[i], keyValues[i + 1]);
        return Map.of("name", name, "props", props);
    }

    private MockHttpServletRequestBuilder 보내기(String visitorId, List<Map<String, Object>> events) throws Exception {
        return 보내기(visitorId, events, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148");
    }

    private MockHttpServletRequestBuilder 보내기(String visitorId, List<Map<String, Object>> events, String userAgent) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("visitorId", visitorId);
        body.put("events", events);
        return post("/events").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body))
            .header(HttpHeaders.USER_AGENT, userAgent);
    }

    private ResultActions 익명으로_보낸다(String visitorId, List<Map<String, Object>> events) throws Exception {
        return mvc.perform(보내기(visitorId, events));
    }

    private ResultActions 기기로_보낸다(Anonymous device, String visitorId, List<Map<String, Object>> events) throws Exception {
        return mvc.perform(보내기(visitorId, events).header(Explorers.TOKEN, device.token()));
    }

    private JsonNode 지표(int days) throws Exception {
        return explorers.json(mvc.perform(get("/admin/metrics").param("days", String.valueOf(days)).header(ADMIN, ADMIN_TOKEN))
            .andExpect(status().isOk()));
    }

    private void 일_배치를_돌린다() throws Exception {
        mvc.perform(post("/admin/metrics/batch").header(ADMIN, ADMIN_TOKEN)).andExpect(status().isOk());
    }

    private int 이벤트_수(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM analytics_event WHERE name = ?", Integer.class, name);
    }

    private String 분석_저장소_전체() {
        StringBuilder all = new StringBuilder();
        TABLES.forEach(table -> all.append(jdbc.queryForList("SELECT * FROM " + table)));
        return all.toString();
    }

    private void 다음_날이_된다() {
        clock.advance(Duration.ofDays(1));
    }

    private JsonNode 마지막(JsonNode array) {
        return array.get(array.size() - 1);
    }

    private JsonNode 그날(JsonNode array, LocalDate day) {
        for (JsonNode node : array) if (node.get("cohortDay").asText().equals(day.toString())) return node;
        throw new AssertionError(day + " 코호트 없음: " + array);
    }

    // ---- 화면 이벤트 ----------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("화면 이벤트 받기")
    class Collecting {

        @Test
        @DisplayName("익명 방문도 보낼 수 있고, 적은 수와 받지 않은 이벤트를 알려 준다")
        void anonymous() throws Exception {
            String visitor = 새_방문();

            익명으로_보낸다(visitor, List.of(이벤트("app_open", "entry", "direct"), 이벤트("buy_item"), 이벤트("tab_view", "tab", "bag")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(2))
                .andExpect(jsonPath("$.rejected[0].index").value(1))
                .andExpect(jsonPath("$.rejected[0].reason").value("UNKNOWN_EVENT"));

            assertThat(jdbc.queryForList("SELECT DISTINCT actor_key FROM analytics_event", String.class)).containsExactly("v:" + visitor);
            assertThat(jdbc.queryForObject("SELECT device FROM analytics_visitor WHERE visitor_id = ?", String.class, visitor))
                .isEqualTo("MOBILE");
        }

        @Test
        @DisplayName("가입 뒤 토큰과 함께 보내면 그 방문이 탐험가와 이어지고, 가입 전 이벤트도 같은 사람으로 센다")
        void linkVisitorToExplorer() throws Exception {
            String visitor = 새_방문();
            익명으로_보낸다(visitor, List.of(이벤트("app_open", "entry", "card"))).andExpect(status().isAccepted());
            Anonymous device = explorers.익명_탐험가();

            기기로_보낸다(device, visitor, List.of(이벤트("tab_view", "tab", "map"))).andExpect(status().isAccepted());

            String hash = jdbc.queryForObject("SELECT explorer_hash FROM analytics_visitor WHERE visitor_id = ?", String.class, visitor);
            assertThat(hash).hasSize(64);
            assertThat(jdbc.queryForList("SELECT DISTINCT actor_key FROM analytics_event WHERE visitor_id = ?", String.class, visitor))
                .containsExactly(hash);
        }

        @Test
        @DisplayName("분석 저장소 어디에도 탐험가 id·토큰이 남지 않는다 — 서버 비밀값을 섞은 해시만 남는다")
        void noRawIdentifiers() throws Exception {
            String visitor = 새_방문();
            Anonymous device = explorers.익명_탐험가();
            기기로_보낸다(device, visitor, List.of(이벤트("app_open", "entry", "direct"))).andExpect(status().isAccepted());
            explorers.칠한다(device, 종로구);
            explorers.전달이_끝날_때까지();

            assertThat(분석_저장소_전체()).doesNotContain(device.id()).doesNotContain(device.token()).doesNotContain(device.personalMapId());
        }

        @Test
        @DisplayName("모르는 토큰이어도 거절하지 않고 익명 방문으로 받는다")
        void unknownToken() throws Exception {
            String visitor = 새_방문();

            mvc.perform(보내기(visitor, List.of(이벤트("checkin_open"))).header(Explorers.TOKEN, "not-a-token"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(1));

            assertThat(jdbc.queryForObject("SELECT actor_key FROM analytics_event", String.class)).isEqualTo("v:" + visitor);
        }

        @Test
        @DisplayName("로그인한 사람은 위조 방지 토큰 없이도 보낼 수 있다 — 페이지를 닫으며 보내는 경우")
        void sessionWithoutCsrf() throws Exception {
            Session session = explorers.로그인();
            String visitor = 새_방문();

            mvc.perform(보내기(visitor, List.of(이벤트("tab_view", "tab", "profile"))).session(session.http()))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(1));

            assertThat(jdbc.queryForObject("SELECT explorer_hash FROM analytics_visitor WHERE visitor_id = ?", String.class, visitor))
                .isNotNull();
        }

        @Test
        @DisplayName("메모·handle 같은 개인정보 필드가 섞인 이벤트는 적지 않는다")
        void personalData() throws Exception {
            익명으로_보낸다(새_방문(), List.of(이벤트("checkin_save", "memo", "엄마랑 남산 산책"), 이벤트("share_click", "target", "card",
                "handle", "hong")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(0))
                .andExpect(jsonPath("$.rejected[0].reason").value("PERSONAL_DATA"))
                .andExpect(jsonPath("$.rejected[1].reason").value("PERSONAL_DATA"));

            assertThat(분석_저장소_전체()).doesNotContain("남산").doesNotContain("hong");
        }

        @Test
        @DisplayName("한 번에 50개를 넘게 보내면 통째로 받지 않는다")
        void tooManyEvents() throws Exception {
            익명으로_보낸다(새_방문(), Collections.nCopies(51, 이벤트("checkin_open")))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("EVENT_BATCH_TOO_LARGE"));
            assertThat(이벤트_수("checkin_open")).isZero();
        }

        @Test
        @DisplayName("본문이 너무 크면 읽기 전에 받지 않는다")
        void tooLargeBody() throws Exception {
            String huge = "{\"visitorId\":\"" + 새_방문() + "\",\"events\":[],\"padding\":\"" + "x".repeat(40_000) + "\"}";

            mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("EVENT_BATCH_TOO_LARGE"));
        }

        @Test
        @DisplayName("방문 ID 가 없거나 형식이 틀리면 받지 않는다")
        void invalidVisitor() throws Exception {
            익명으로_보낸다("x", List.of(이벤트("checkin_open"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VISITOR_ID"));
            익명으로_보낸다(null, List.of(이벤트("checkin_open"))).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("같은 방문이 몰아서 너무 자주 보내면 잠시 받지 않는다")
        void rateLimited() throws Exception {
            String visitor = 새_방문();
            for (int i = 0; i < 20; i++) 익명으로_보낸다(visitor, List.of(이벤트("checkin_open"))).andExpect(status().isAccepted());

            익명으로_보낸다(visitor, List.of(이벤트("checkin_open"))).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EVENTS_RATE_LIMITED"));
            익명으로_보낸다(새_방문(), List.of(이벤트("checkin_open"))).andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("봇이 보낸 이벤트는 적지 않는다(오류도 아니다)")
        void bot() throws Exception {
            mvc.perform(보내기(새_방문(), List.of(이벤트("app_open", "entry", "direct")), "curl/8.4.0"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(0));

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_event", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_visitor", Integer.class)).isZero();
        }

        @Test
        @DisplayName("빈 묶음이나 모두 받지 않은 묶음은 새 방문·첫 화면으로 세지 않는다")
        void emptyBatchIsNotAVisit() throws Exception {
            익명으로_보낸다(새_방문(), List.of()).andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(0));
            익명으로_보낸다(새_방문(), List.of(이벤트("buy_item"), 이벤트("check_in", "rarity", "common")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(0));
            mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content("{\"visitorId\":\"" + 새_방문() + "\"}")
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (iPhone) Mobile")).andExpect(status().isAccepted());

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_visitor", Integer.class)).isZero();
            assertThat(지표(1).get("today").get("newVisitors").asInt()).isZero();
        }

        @Test
        @ExtendWith(OutputCaptureExtension.class)
        @DisplayName("이상한 본문은 모두 요청 오류로 거절하고 서버 오류 기록을 남기지 않는다")
        void fuzz(CapturedOutput output) throws Exception {
            String visitor = 새_방문();
            List<String> bodies = List.of(
                "{\"visitorId\":\"" + visitor + "\",\"events\":[null]}",
                "{\"visitorId\":\"" + visitor + "\",\"events\":[{\"name\":\"tab_view\"},null]}",
                "{\"visitorId\":\"" + visitor + "\",\"events\":\"tab_view\"}",
                "{\"visitorId\":\"" + visitor + "\",\"events\":[\"tab_view\"]}",
                "{\"visitorId\":\"" + visitor + "\",\"events\":[[1,2]]}",
                "{\"visitorId\":\"" + visitor + "\",\"events\":[{\"name\":{\"a\":1}}]}",
                "{\"visitorId\":\"" + visitor + "\",\"events\":[{\"name\":\"tab_view\",\"props\":\"map\"}]}",
                "{\"visitorId\":\"" + visitor + "\",\"events\":[{\"name\":\"tab_view\",\"props\":[1]}]}",
                "{\"visitorId\":\"" + visitor + "\",\"events\":[{\"name\":\"tab_view\",\"at\":{\"x\":1}}]}",
                "{\"visitorId\":{\"id\":1},\"events\":[]}",
                "{\"visitorId\":[\"" + visitor + "\"],\"events\":[]}",
                "{\"visitorId\":\"\",\"events\":[]}",
                "{\"visitorId\":null,\"events\":null}",
                "null", "[]", "\"text\"", "", "{", "{\"visitorId\":\"" + "v".repeat(20_000) + "\",\"events\":[]}");
            for (String body : bodies) {
                int status = mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (iPhone) Mobile")).andReturn().getResponse().getStatus();
                assertThat(status).as(body.length() > 120 ? body.substring(0, 120) : body).isBetween(400, 499);
            }

            List<Map<String, Object>> oddFields = List.of(
                Map.of("name", "tab_view", "props", Map.of("tab", Map.of("nested", List.of(1, 2)))),
                Map.of("name", "tab_view", "props", Map.of("tab", "m".repeat(10_000))),
                Map.of("name", "onboarding_step", "props", Map.of("step", "1e400")),
                Map.of("name", "", "props", Map.of()),
                Map.of("name", "x".repeat(5_000)));
            익명으로_보낸다(새_방문(), oddFields).andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(0))
                .andExpect(jsonPath("$.rejected.length()").value(5));

            assertThat(output.getAll()).doesNotContain("처리하지 못한 예외").doesNotContain(" ERROR ");
        }

        @Test
        @DisplayName("정해진 형식(JSON)이 아닌 본문은 받지 않는다 — 다른 사이트의 폼으로는 보낼 수 없게")
        void jsonOnly() throws Exception {
            mvc.perform(post("/events").contentType(MediaType.TEXT_PLAIN).content("{\"visitorId\":\"" + 새_방문() + "\",\"events\":[]}"))
                .andExpect(status().isUnsupportedMediaType());
        }
    }

    // ---- 서버 사실 ------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("서버 사실 적기")
    class ServerFacts {

        @Test
        @DisplayName("가입·체크인이 적히고, 첫 체크인은 처음 한 번만 적힌다")
        void createdAndFirstCheckIn() throws Exception {
            Anonymous device = explorers.익명_탐험가();
            explorers.칠한다(device, 종로구);
            explorers.칠한다(device, 중구);
            explorers.전달이_끝날_때까지();

            assertThat(이벤트_수("explorer_created")).isEqualTo(1);
            assertThat(이벤트_수("check_in")).isEqualTo(2);
            assertThat(이벤트_수("first_check_in")).isEqualTo(1);
            assertThat(jdbc.queryForMap("SELECT created_day, first_check_in_day FROM analytics_explorer").values())
                .allSatisfy(day -> assertThat(day.toString()).isEqualTo(LocalDate.now(clock).toString()));
        }

        @Test
        @DisplayName("같은 사실이 다시 전달돼도 한 번만 적힌다")
        void idempotent() throws Exception {
            Anonymous device = explorers.익명_탐험가();
            explorers.칠한다(device, 종로구);
            explorers.전달이_끝날_때까지();
            EventSubscriber analytics = subscribers.stream()
                .filter(subscriber -> subscriber.id().equals(AnalyticsSubscriptions.SUBSCRIBER)).findFirst().orElseThrow();
            RegionVisited visited = captured.all().stream().filter(RegionVisited.class::isInstance).map(RegionVisited.class::cast)
                .filter(event -> event.explorerId().equals(device.id())).findFirst().orElseThrow();

            analytics.handle(visited);
            analytics.handle(visited);

            assertThat(이벤트_수("check_in")).isEqualTo(1);
            assertThat(이벤트_수("first_check_in")).isEqualTo(1);
        }

        @Test
        @DisplayName("초대코드로 합류했는지 공개 프로필 링크로 합류했는지 나눠 적는다")
        void joinRoutes() throws Exception {
            Anonymous owner = explorers.익명_탐험가();
            JsonNode map = explorers.공유_지도를_만든다(owner, "서울 원정대");
            explorers.합류한다(explorers.익명_탐험가(), map.get("inviteCode").asText());

            Session host = explorers.로그인();
            explorers.공개_범위(host, "PUBLIC");
            String hostMap = explorers.json(explorers.세션으로(host, post("/maps").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"부산 원정대\"}")).andExpect(status().isCreated())).get("mapId").asText();
            explorers.세션으로(host, put("/maps/" + hostMap + "/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"photoRequired\":false,\"dailyCheckInCap\":5,\"visibility\":\"PUBLIC\"}")).andExpect(status().isOk());
            explorers.기기로(explorers.익명_탐험가(), post("/maps/join-via-profile/" + host.handle())
                .contentType(MediaType.APPLICATION_JSON).content("{\"mapId\":\"" + hostMap + "\"}")).andExpect(status().isOk());
            explorers.전달이_끝날_때까지();

            assertThat(jdbc.queryForList("SELECT label FROM analytics_event WHERE name = 'shared_map_joined' ORDER BY id", String.class))
                .containsExactly("invite_code", "profile_link");
            assertThat(이벤트_수("shared_map_created")).isEqualTo(2);
        }

        @Test
        @DisplayName("합류 경로가 실리기 전에 쌓인 예전 합류 소식은 경로 모름으로 센다")
        void legacyJoinWithoutVia() throws Exception {
            Anonymous guest = explorers.익명_탐험가();
            explorers.전달이_끝날_때까지();
            String legacy = "{\"mapId\":\"" + UUID.randomUUID() + "\",\"explorerId\":\"" + guest.id() + "\",\"role\":\"MEMBER\","
                + "\"joinedAt\":\"2026-10-02T03:00:00Z\",\"rejoined\":false,\"invitedBy\":\"" + UUID.randomUUID() + "\"}";
            MemberJoined event = om.readValue(legacy, MemberJoined.class);

            subscribers.stream().filter(subscriber -> subscriber.id().equals(AnalyticsSubscriptions.SUBSCRIBER)).findFirst()
                .orElseThrow().handle(event);

            assertThat(event.joinedVia()).isNull();
            assertThat(jdbc.queryForObject("SELECT label FROM analytics_event WHERE name = 'shared_map_joined'", String.class))
                .isEqualTo("unknown");
            assertThat(jdbc.queryForObject("SELECT invite_acquired FROM analytics_explorer", Boolean.class)).isTrue();
        }

        @Test
        @DisplayName("로그인으로 계정을 처음 연결하면 계정 연결이 적히고 handle 은 적히지 않는다")
        void accountLinked() throws Exception {
            Session session = explorers.로그인(explorers.익명_탐험가(), Explorers.새_이메일("link"));
            explorers.전달이_끝날_때까지();

            assertThat(이벤트_수("account_linked")).isEqualTo(1);
            assertThat(분석_저장소_전체()).doesNotContain(session.handle());
        }
    }

    // ---- 공개 페이지 열람 --------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("공개 페이지 열람")
    class PublicViews {

        @Test
        @DisplayName("공개 프로필을 열면 열람이 적히고 handle 은 적히지 않으며, 앱으로 가는 링크는 프로필에서 왔다고 알린다")
        void profileView() throws Exception {
            Session host = explorers.로그인();
            explorers.공개_범위(host, "PUBLIC");

            mvc.perform(get("/u/" + host.handle()).header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (iPhone) Mobile"))
                .andExpect(status().isOk());

            assertThat(jdbc.queryForMap("SELECT name, device, actor_key FROM analytics_event WHERE source = 'REQUEST'"))
                .containsEntry("NAME", "profile_view").containsEntry("DEVICE", "MOBILE").containsEntry("ACTOR_KEY", null);
            assertThat(mvc.perform(get("/u/" + host.handle())).andReturn().getResponse().getContentAsString())
                .contains("href=\"/?from=profile\"");
            assertThat(분석_저장소_전체()).doesNotContain(host.handle());
        }

        @Test
        @DisplayName("없거나 비공개인 프로필은 열람으로 세지 않는다")
        void notFound() throws Exception {
            Session hidden = explorers.로그인();
            mvc.perform(get("/u/nobody-here")).andExpect(status().isNotFound());
            mvc.perform(get("/u/" + hidden.handle())).andExpect(status().isNotFound());

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_event WHERE source = 'REQUEST'", Integer.class)).isZero();
        }

        @Test
        @DisplayName("링크 미리보기 봇이 카드를 가져가면 봇 열람으로 따로 적는다")
        void botCardView() throws Exception {
            Session host = explorers.로그인();
            explorers.공개_범위(host, "PUBLIC");

            mvc.perform(get("/u/" + host.handle() + "/card/territory.png").header(HttpHeaders.USER_AGENT, "facebookexternalhit/1.1;kakaotalk-scrap"))
                .andExpect(status().isOk());

            assertThat(jdbc.queryForMap("SELECT name, device, label FROM analytics_event WHERE source = 'REQUEST'"))
                .containsEntry("NAME", "card_view").containsEntry("DEVICE", "BOT").containsEntry("LABEL", "territory");
            assertThat(지표(1).get("daily").get(0).get("botViews").asInt()).isEqualTo(1);
        }
    }

    // ---- 운영 지표 ------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("운영 지표")
    class Metrics {

        @Test
        @DisplayName("관리자 토큰이 없으면 볼 수 없다")
        void adminOnly() throws Exception {
            mvc.perform(get("/admin/metrics")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_TOKEN_REQUIRED"));
            mvc.perform(get("/admin/metrics").header(ADMIN, "wrong")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_TOKEN_INVALID"));
            mvc.perform(post("/admin/metrics/batch").with(csrf())).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("운영자는 첫 화면부터 첫 체크인까지의 퍼널을 오늘 바로 볼 수 있다")
        void funnelToday() throws Exception {
            String visitor = 새_방문();
            익명으로_보낸다(visitor, List.of(이벤트("app_open", "entry", "direct"))).andExpect(status().isAccepted());
            Anonymous device = explorers.익명_탐험가();
            기기로_보낸다(device, visitor, List.of(이벤트("checkin_open"), 이벤트("checkin_save"))).andExpect(status().isAccepted());
            explorers.칠한다(device, 종로구);
            explorers.전달이_끝날_때까지();
            익명으로_보낸다(새_방문(), List.of(이벤트("app_open", "entry", "direct"))).andExpect(status().isAccepted());

            JsonNode metrics = 지표(7);

            JsonNode today = 마지막(metrics.get("funnel"));
            assertThat(today.get("cohortDay").asText()).isEqualTo(LocalDate.now(clock).toString());
            assertThat(today.get("firstScreen").asInt()).isEqualTo(2);
            assertThat(today.get("firstCheckIn").asInt()).isEqualTo(1);
            assertThat(today.get("checkInRate").asDouble()).isEqualTo(0.5);
            assertThat(today.get("revisitedWithin7Days").asInt()).isZero();
            assertThat(metrics.get("today").get("newVisitors").asInt()).isEqualTo(2);
            assertThat(metrics.get("today").get("newExplorers").asInt()).isEqualTo(1);
            assertThat(metrics.get("today").get("dau").asInt()).isEqualTo(2);
            assertThat(마지막(metrics.get("daily")).get("live").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("운영자는 다음 날 다시 온 탐험가를 D1 리텐션과 7일 내 재방문으로 볼 수 있다")
        void retentionAndRevisit() throws Exception {
            LocalDate joined = LocalDate.now(clock);
            String visitor = 새_방문();
            Anonymous returning = explorers.익명_탐험가();
            기기로_보낸다(returning, visitor, List.of(이벤트("app_open", "entry", "direct"))).andExpect(status().isAccepted());
            explorers.칠한다(returning, 종로구);
            Anonymous leaving = explorers.익명_탐험가();
            explorers.칠한다(leaving, 중구);
            explorers.전달이_끝날_때까지();

            다음_날이_된다();
            기기로_보낸다(returning, visitor, List.of(이벤트("tab_view", "tab", "map"))).andExpect(status().isAccepted());
            다음_날이_된다();
            일_배치를_돌린다();

            JsonNode metrics = 지표(7);
            JsonNode retention = 그날(metrics.get("retention"), joined);
            assertThat(retention.get("newExplorers").asInt()).isEqualTo(2);
            assertThat(retention.get("d1").asInt()).isEqualTo(1);
            assertThat(retention.get("d1Rate").asDouble()).isEqualTo(0.5);
            assertThat(retention.get("d7").isNull()).isTrue();
            JsonNode funnel = 그날(metrics.get("funnel"), joined);
            assertThat(funnel.get("firstScreen").asInt()).isEqualTo(1);
            assertThat(funnel.get("firstCheckIn").asInt()).isEqualTo(1);
            assertThat(funnel.get("revisitedWithin7Days").asInt()).isEqualTo(1);
            assertThat(funnel.get("settled").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("일 배치를 다시 돌려도 같은 값이다 — 두 번째부터 하루 지표는 최근 3일, 코호트는 최근 35일만 다시 센다")
        void batchIsRepeatable() throws Exception {
            Anonymous device = explorers.익명_탐험가();
            explorers.칠한다(device, 종로구);
            explorers.전달이_끝날_때까지();
            다음_날이_된다();
            일_배치를_돌린다();
            List<Map<String, Object>> first = jdbc.queryForList("SELECT metric_day, dau, wau, mau, new_explorers FROM analytics_daily "
                + "ORDER BY metric_day");

            explorers.json(mvc.perform(post("/admin/metrics/batch").header(ADMIN, ADMIN_TOKEN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyDays").value(3)).andExpect(jsonPath("$.cohortDays").value(35)));

            assertThat(jdbc.queryForList("SELECT metric_day, dau, wau, mau, new_explorers FROM analytics_daily ORDER BY metric_day"))
                .isEqualTo(first).hasSize(90);
            assertThat(지표(3).get("missingDays")).isEmpty();
        }

        @Test
        @DisplayName("90일 보기의 빈 날은 일 배치를 돌리면 실제로 모두 채워진다")
        void batchFillsWholeReportRange() throws Exception {
            assertThat(지표(90).get("missingDays")).hasSize(89);

            일_배치를_돌린다();

            JsonNode metrics = 지표(90);
            assertThat(metrics.get("missingDays")).isEmpty();
            assertThat(metrics.get("expiredDays")).isEmpty();
            assertThat(metrics.get("daily")).hasSize(90);
            assertThat(metrics.get("retention")).hasSize(90);

            jdbc.update("DELETE FROM analytics_daily WHERE metric_day = ?", LocalDate.now(clock).minusDays(70));
            jdbc.update("DELETE FROM analytics_cohort WHERE cohort_day = ?", LocalDate.now(clock).minusDays(70));
            assertThat(지표(90).get("missingDays")).hasSize(1);
            explorers.json(mvc.perform(post("/admin/metrics/batch").header(ADMIN, ADMIN_TOKEN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyDays").value(4)).andExpect(jsonPath("$.cohortDays").value(36)));
            assertThat(지표(90).get("missingDays")).isEmpty();
        }

        @Test
        @DisplayName("90일이 지난 원본 이벤트는 일 배치가 지우고 집계는 남는다")
        void purgeOldRawEvents() throws Exception {
            LocalDate today = LocalDate.now(clock);
            for (LocalDate day : List.of(today.minusDays(91), today.minusDays(90))) {
                jdbc.update("INSERT INTO analytics_event (name, source, occurred_at, event_day, actor_key, device, props) "
                    + "VALUES ('tab_view', 'CLIENT', ?, ?, 'v:old-visitor-1', 'MOBILE', '{}')", day.atTime(3, 0), day);
            }

            일_배치를_돌린다();

            assertThat(jdbc.queryForList("SELECT event_day FROM analytics_event", LocalDate.class)).containsExactly(today.minusDays(90));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_daily", Integer.class)).isEqualTo(90);
        }

        @Test
        @DisplayName("마지막 활동이 보관 기간보다 오래된 방문·여정은 일 배치가 지우고, 최근에 활동한 사람은 남긴다")
        void forgetInactiveIdentities() throws Exception {
            String visitor = 새_방문();
            Anonymous device = explorers.익명_탐험가();
            기기로_보낸다(device, visitor, List.of(이벤트("app_open", "entry", "direct"))).andExpect(status().isAccepted());
            explorers.칠한다(device, 종로구);
            explorers.전달이_끝날_때까지();
            LocalDate longAgo = LocalDate.now(clock).minusDays(200);
            jdbc.update("INSERT INTO analytics_visitor (visitor_id, first_seen_at, first_seen_day, device) VALUES ('old-visitor-01', ?, ?, "
                + "'MOBILE')", longAgo.atTime(3, 0), longAgo);
            jdbc.update("INSERT INTO analytics_explorer (explorer_hash, created_day) VALUES (?, ?)", "f".repeat(64), longAgo);

            explorers.json(mvc.perform(post("/admin/metrics/batch").header(ADMIN, ADMIN_TOKEN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.purgedVisitors").value(1)).andExpect(jsonPath("$.purgedJourneys").value(1)));

            assertThat(jdbc.queryForList("SELECT visitor_id FROM analytics_visitor", String.class)).containsExactly(visitor);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_explorer", Integer.class)).isEqualTo(1);
        }

        @Test
        @DisplayName("운영자는 카드로 들어와 가입한 사람과 초대로 합류한 새 탐험가로 K 계수를 볼 수 있다")
        void kFactor() throws Exception {
            Anonymous host = explorers.익명_탐험가();
            JsonNode map = explorers.공유_지도를_만든다(host, "우리 동네");
            String cardVisitor = 새_방문();
            익명으로_보낸다(cardVisitor, List.of(이벤트("app_open", "entry", "card"))).andExpect(status().isAccepted());
            Anonymous fromCard = explorers.익명_탐험가();
            기기로_보낸다(fromCard, cardVisitor, List.of(이벤트("tab_view", "tab", "map"))).andExpect(status().isAccepted());
            Anonymous invited = explorers.익명_탐험가();
            explorers.합류한다(invited, map.get("inviteCode").asText());
            explorers.전달이_끝날_때까지();

            JsonNode kFactor = 지표(1).get("kFactor");

            assertThat(kFactor.get("cardNewExplorers").asInt()).isEqualTo(1);
            assertThat(kFactor.get("invitedNewExplorers").asInt()).isEqualTo(1);
            assertThat(kFactor.get("viralNewExplorers").asInt()).isEqualTo(2);
            assertThat(kFactor.get("activeExplorers").asInt()).isEqualTo(3);
            assertThat(kFactor.get("value").asDouble()).isEqualTo(0.6667);
        }

        @Test
        @DisplayName("운영자는 기능별 사용률과 많이 뜬 오류 코드를 볼 수 있다")
        void featuresAndErrors() throws Exception {
            String first = 새_방문();
            String second = 새_방문();
            익명으로_보낸다(first, List.of(이벤트("tab_view", "tab", "bag"), 이벤트("error_toast", "code", "DAILY_CAP_EXCEEDED"),
                이벤트("error_toast", "code", "DAILY_CAP_EXCEEDED"))).andExpect(status().isAccepted());
            익명으로_보낸다(second, List.of(이벤트("share_click", "target", "card"), 이벤트("error_toast", "code", "MAP_FULL")))
                .andExpect(status().isAccepted());

            JsonNode metrics = 지표(1);

            JsonNode features = metrics.get("featureUsage");
            assertThat(features.get("activeUsers").asInt()).isEqualTo(2);
            List<String> used = new ArrayList<>();
            features.get("features").forEach(feature -> {
                if (feature.get("users").asInt() > 0) used.add(feature.get("name").asText() + "=" + feature.get("rate").asDouble());
            });
            assertThat(used).containsExactlyInAnyOrder("tab_view=0.5", "share_click=0.5");
            assertThat(metrics.get("topErrors").get("codes").get(0).get("code").asText()).isEqualTo("DAILY_CAP_EXCEEDED");
            assertThat(metrics.get("topErrors").get("codes").get(0).get("count").asInt()).isEqualTo(2);
            assertThat(metrics.get("topErrors").get("total").asInt()).isEqualTo(3);
        }

        @Test
        @DisplayName("한 번에 90일보다 길게는 볼 수 없다")
        void rangeLimit() throws Exception {
            mvc.perform(get("/admin/metrics").param("days", "91").header(ADMIN, ADMIN_TOKEN)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_METRICS_RANGE"));
        }
    }

    @Nested
    @DisplayName("개발 도구")
    class DevTools {

        @Test
        @DisplayName("개발자는 시드로 지난 한 달치 지표 화면을 채워 볼 수 있다")
        void seed() throws Exception {
            mvc.perform(post("/dev/analytics/seed").param("days", "10").param("visitorsPerDay", "12")).andExpect(status().isOk())
                .andExpect(jsonPath("$.seeded.days").value(10));

            JsonNode metrics = 지표(10);

            assertThat(metrics.get("daily")).hasSize(10);
            assertThat(metrics.get("missingDays")).isEmpty();
            assertThat(metrics.get("funnel").get(0).get("firstScreen").asInt()).isPositive();
            assertThat(metrics.get("retention").get(0).get("d1")).isNotNull();
            assertThat(metrics.get("kFactor").get("activeExplorers").asInt()).isPositive();
        }
    }
}
