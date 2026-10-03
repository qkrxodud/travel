package com.kobi.territory.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 통합 테스트의 준비 문장 — 탐험가를 발급하고, 로그인하고, 지역을 칠하고, 지도를 만들고, 소식이 모두 전달될 때까지 기다린다.
 * HTTP(실제 화면과 같은 경로)로만 움직인다. {@link IntegrationTest} 컨텍스트에 빈으로 들어 있다.
 */
public class Explorers {

    public static final String TOKEN = "X-Explorer-Token";
    public static final Duration WAIT = Duration.ofSeconds(20);

    /** 익명 탐험가(기기 하나). */
    public record Anonymous(String id, String token, String personalMapId) {}

    /** 로그인한 탐험가(세션 + 로그인 응답). */
    public record Session(MockHttpSession http, JsonNode login) {
        public String explorerId() { return login.get("explorerId").asText(); }
        public String handle() { return login.get("handle").asText(); }
        public String personalMapId() { return login.get("personalMapId").asText(); }
        public String outcome() { return login.get("outcome").asText(); }
    }

    private final MockMvc mvc;
    private final ObjectMapper om;
    private final MutableClock clock;
    private final JdbcTemplate jdbc;

    public Explorers(MockMvc mvc, ObjectMapper om, MutableClock clock, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.om = om;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    // ---- 탐험가 ----

    /** 새 기기에서 익명 탐험가를 발급받는다. */
    public Anonymous 익명_탐험가() throws Exception {
        JsonNode body = json(mvc.perform(post("/explorers")).andExpect(status().isCreated()));
        return new Anonymous(body.get("explorerId").asText(), body.get("accessToken").asText(), body.get("personalMapId").asText());
    }

    /** 처음 보는 이메일. */
    public static String 새_이메일(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    /** 새 계정으로 로그인한다(기기 없음 → 새 탐험가). */
    public Session 로그인() throws Exception {
        return 로그인(null, 새_이메일("explorer"));
    }

    /** 이 기기(익명 탐험가)에서 이 이메일 계정으로 로그인한다 — 처음이면 연결, 계정이 있으면 병합. */
    public Session 로그인(Anonymous device, String email) throws Exception {
        MockHttpServletRequestBuilder request = post("/dev/login").contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(Map.of("email", email)));
        if (device != null) request = request.header(TOKEN, device.token());
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false),
            om.readTree(result.getResponse().getContentAsString()));
    }

    /** 로그인 세션으로 요청한다(화면처럼 위조 방지 토큰을 붙인다). */
    public ResultActions 세션으로(Session session, MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder.session(session.http()).with(csrf()));
    }

    /** 익명 기기로 요청한다. */
    public ResultActions 기기로(Anonymous device, MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder.header(TOKEN, device.token()));
    }

    /** 공개 범위를 바꾼다(PUBLIC·FRIENDS·PRIVATE). */
    public void 공개_범위(Session session, String visibility) throws Exception {
        세션으로(session, put("/me/privacy").contentType(MediaType.APPLICATION_JSON)
            .content("{\"visibility\":\"" + visibility + "\"}")).andExpect(status().isOk());
    }

    // ---- 칠하기 ----

    /** 개인 지도에 오늘 날짜로 칠한다(처리 시각 1초 흐름). */
    public void 칠한다(Anonymous who, String code) throws Exception {
        칠한다(who, null, code);
    }

    /** 이 지도(없으면 개인 지도)에 오늘 날짜로 칠한다(처리 시각 1초 흐름). */
    public void 칠한다(Anonymous who, String mapId, String code) throws Exception {
        체크인(who, 방문(code, LocalDate.now(clock), null, mapId)).andExpect(status().isCreated());
    }

    /** 로그인 세션으로 이 지도(없으면 개인 지도)에 오늘 날짜로 칠한다. */
    public void 칠한다(Session who, String mapId, String code) throws Exception {
        clock.advance(Duration.ofSeconds(1));
        세션으로(who, post("/visits").contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(방문(code, LocalDate.now(clock), null, mapId)))).andExpect(status().isCreated());
    }

    /** 체크인 요청 하나(처리 시각 1초 흐름, 결과는 호출자가 본다). */
    public ResultActions 체크인(Anonymous who, Map<String, Object> visit) throws Exception {
        clock.advance(Duration.ofSeconds(1));
        return mvc.perform(post("/visits").header(TOKEN, who.token()).contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(visit)));
    }

    /** 체크인 본문. 메모·지도는 없으면 뺀다. */
    public static Map<String, Object> 방문(String code, LocalDate date, String memo, String mapId) {
        Map<String, Object> body = new HashMap<>(Map.of("regionCode", code, "visitDate", date.toString()));
        if (memo != null) body.put("memo", memo);
        if (mapId != null) body.put("mapId", mapId);
        return body;
    }

    // ---- 공유 지도 ----

    /** 이 기기로 공유 지도를 만든다(응답 본문: 지도 id·초대코드). */
    public JsonNode 공유_지도를_만든다(Anonymous owner, String name) throws Exception {
        return json(기기로(owner, post("/maps").contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(Map.of("name", name)))).andExpect(status().isCreated()));
    }

    /** 초대코드로 합류한다. */
    public void 합류한다(Anonymous who, String inviteCode) throws Exception {
        기기로(who, post("/maps/join").contentType(MediaType.APPLICATION_JSON)
            .content("{\"inviteCode\":\"" + inviteCode + "\"}")).andExpect(status().isOk());
    }

    /** 지도 멤버가 보는 지금 초대코드. */
    public String 초대코드(Anonymous member, String mapId) throws Exception {
        return json(기기로(member, get("/maps/" + mapId))).get("inviteCode").asText();
    }

    // ---- 기다리기 ----

    /** 쌓인 소식이 모든 구독자에게 전달되고 예약된 재계산이 끝날 때까지 기다린다. */
    public void 전달이_끝날_때까지() {
        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recalculation_request", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class)).isZero();
        });
    }

    /** 이 지도·탐험가들 앞으로 쌓인 소식이 모두 전달될 때까지 기다린다. */
    public void 전달이_끝날_때까지(String... aggregateIds) {
        await().atMost(WAIT).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE published_at IS NULL "
            + "AND aggregate_id IN (" + String.join(",", java.util.Collections.nCopies(aggregateIds.length, "?")) + ")",
            Integer.class, (Object[]) aggregateIds) == 0);
    }

    // ---- 응답 ----

    public JsonNode json(ResultActions result) throws Exception {
        return om.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
