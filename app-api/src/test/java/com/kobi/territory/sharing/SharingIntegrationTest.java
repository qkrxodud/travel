package com.kobi.territory.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.exploration.api.event.MemberJoined;
import com.kobi.territory.sharing.application.ShowcaseReader;
import com.kobi.territory.sharing.domain.card.CardKind;
import com.kobi.territory.sharing.domain.showcase.CardComposer;
import com.kobi.territory.sharing.domain.showcase.CardContent;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 4단계 파트 B D2·D3: 카드 lazy 렌더(첫 요청 렌더 → 캐시 → 공개 요약이 바뀌면 낡음 → 최소 TTL 뒤 다시 렌더), 병합 직후 첫 렌더가
 * 재계산 뒤 낡음으로 잡힘(QA P2-1)·칭호만 바꿔도 낡음, 로그인 직후 공개 카드가 익명 카드가 아님(QA P2-2), 공개 프로필 HTML·OG meta
 * (메모·사진 미노출, 월 단위 날짜, og:image = 설정 기준 주소 — Host 헤더 무시), 공개 범위(기본 PRIVATE, 공개하기, PRIVATE·FRIENDS
 * → 404 존재 숨김), 프로필 링크 합류 + 초대 보상(양쪽, 같은 쌍 1회, 재가입·셀프 없음, 프로필 비공개면 404·보상 없음), 초대코드 합류
 * 보상, 병합 시 초대 보상 이전, VS 비저장, MemberJoined 하위 호환.
 */
@IntegrationTest
class SharingIntegrationTest {

    private static final String H = "X-Explorer-Token";
    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final String MEMO = "비밀메모-" + UUID.randomUUID().toString().substring(0, 4);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired ShowcaseReader showcases;

    record Anonymous(String id, String token, String personalMapId) {}

    record Session(MockHttpSession http, String explorerId, String handle) {}

    private Anonymous anonymous() throws Exception {
        JsonNode body = json(mvc.perform(post("/explorers")).andExpect(status().isCreated()));
        return new Anonymous(body.get("explorerId").asText(), body.get("accessToken").asText(), body.get("personalMapId").asText());
    }

    private Session login(Anonymous device) throws Exception {
        return login(device, "share" + UUID.randomUUID().toString().substring(0, 6) + "@example.com");
    }

    private void publish(Session session) throws Exception {
        as(session, put("/me/privacy").contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PUBLIC\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.publiclyVisible").value(true));
    }

    private Session login(Anonymous device, String email) throws Exception {
        MvcResult result = mvc.perform(post("/dev/login").header(H, device.token()).contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(Map.of("email", email)))).andExpect(status().isOk()).andReturn();
        JsonNode body = om.readTree(result.getResponse().getContentAsString());
        return new Session((MockHttpSession) result.getRequest().getSession(false), body.get("explorerId").asText(),
            body.get("handle").asText());
    }

    private void awaitSettled() {
        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recalculation_request", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class)).isZero();
        });
    }

    private ResultActions as(Session session, MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder.session(session.http()).with(csrf()));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return om.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private void checkIn(Anonymous who, String code, LocalDate date, String memo) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("regionCode", code, "visitDate", date.toString()));
        if (memo != null) body.put("memo", memo);
        clock.advance(Duration.ofSeconds(1));
        mvc.perform(post("/visits").header(H, who.token()).contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(body))).andExpect(status().isCreated());
    }

    private void checkIn(Session who, String code, LocalDate date, String memo) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("regionCode", code, "visitDate", date.toString(), "memo", memo));
        clock.advance(Duration.ofSeconds(1));
        as(who, post("/visits").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
            .andExpect(status().isCreated());
    }

    private Instant renderedAt(String explorerId, String kind) {
        List<Instant> rows = jdbc.queryForList("SELECT rendered_at FROM share_card WHERE explorer_id = ? AND kind = ?",
            Instant.class, explorerId, kind);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String renderedHandle(String explorerId, String kind) {
        List<String> rows = jdbc.queryForList("SELECT rendered_handle FROM share_card WHERE explorer_id = ? AND kind = ?",
            String.class, explorerId, kind);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<String> owned(String explorerId, String prefix) {
        return jdbc.queryForList("SELECT item_id FROM owned_item WHERE explorer_id = ? AND item_id LIKE ? ORDER BY item_id",
            String.class, explorerId, prefix + "%");
    }

    private static BufferedImage png(MvcResult result) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
    }

    // ---- 카드 lazy 렌더 ------------------------------------------------------------------------------------------

    @Test
    void 카드는_첫_요청에_그리고_캐시하며_요약이_바뀌면_최소_TTL_뒤에_다시_그린다() throws Exception {
        Anonymous me = anonymous();
        checkIn(me, "KR-11010", LocalDate.now(clock), MEMO);
        awaitSettled();

        mvc.perform(get("/me/cards").header(H, me.token())).andExpect(status().isOk())
            .andExpect(jsonPath("$.handle").doesNotExist())
            .andExpect(jsonPath("$.cards[0].kind").value("TERRITORY"))
            .andExpect(jsonPath("$.cards[0].rendered").value(false));
        MvcResult first = mvc.perform(get("/me/cards/territory.png").header(H, me.token())).andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG)).andReturn();
        assertThat(png(first).getWidth()).isEqualTo(1200);
        Instant firstRender = renderedAt(me.id(), "TERRITORY");
        assertThat(firstRender).isNotNull();

        clock.advance(Duration.ofMinutes(1));
        mvc.perform(get("/me/cards/territory.png").header(H, me.token())).andExpect(status().isOk());
        assertThat(renderedAt(me.id(), "TERRITORY")).as("원천 그대로 — 캐시").isEqualTo(firstRender);

        checkIn(me, "KR-11020", LocalDate.now(clock), null);
        awaitSettled();
        mvc.perform(get("/me/cards").header(H, me.token())).andExpect(jsonPath("$.cards[0].stale").value(true));
        mvc.perform(get("/me/cards/territory.png").header(H, me.token())).andExpect(status().isOk());
        assertThat(renderedAt(me.id(), "TERRITORY")).as("낡았지만 최소 TTL 10분 전 — 그린 카드 그대로").isEqualTo(firstRender);

        clock.advance(Duration.ofMinutes(10));
        mvc.perform(get("/me/cards/territory.png").header(H, me.token())).andExpect(status().isOk());
        assertThat(renderedAt(me.id(), "TERRITORY")).as("TTL 지남 — 다시 그림").isAfter(firstRender);
        mvc.perform(get("/me/cards").header(H, me.token())).andExpect(jsonPath("$.cards[0].stale").value(false))
            .andExpect(jsonPath("$.cards[0].rendered").value(true));

        mvc.perform(get("/me/cards/vs.png").header(H, me.token())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CARD_KIND_NOT_FOUND"));
        mvc.perform(get("/me/cards")).andExpect(status().isUnauthorized());
    }

    // ---- 공개 프로필 · 공개 범위 ---------------------------------------------------------------------------------

    @Test
    void 공개_프로필은_로그아웃_상태로_열리고_메모_사진_정확한_날짜는_없고_OG_이미지는_PNG() throws Exception {
        Anonymous device = anonymous();
        checkIn(device, "KR-11010", LocalDate.of(2026, 9, 17), MEMO);
        Session owner = login(device);
        checkIn(owner, "KR-26010", LocalDate.of(2026, 10, 2), MEMO + "2");
        // 기본 공개 범위 PRIVATE(사용자 결정 Q1) — "공개하기" 전에는 존재도 숨긴다
        mvc.perform(get("/u/" + owner.handle())).andExpect(status().isNotFound());
        as(owner, get("/me/privacy")).andExpect(jsonPath("$.visibility").value("PRIVATE"));
        publish(owner);

        await().atMost(WAIT).untilAsserted(() -> mvc.perform(get("/u/" + owner.handle()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-region=\"KR-26010\""))));
        String html = mvc.perform(get("/u/" + owner.handle()).header("Host", "evil.example")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML)).andReturn().getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("<meta property=\"og:image\" content=\"http://localhost:8080/u/" + owner.handle() + "/card/territory.png\">")
            .contains("og:title").contains("og:description")
            .contains("2026년 9월").contains("2026년 10월").contains("data-month=\"2026-10\"")
            .doesNotContain(MEMO).doesNotContain("2026-09-17").doesNotContain("2026-10-02").doesNotContain("photo")
            .doesNotContain("evil.example"); // 절대 주소는 설정값 territory.public-base-url(QA P3-2)

        mvc.perform(get("/u/" + owner.handle() + "/card/territory.png")).andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG)).andExpect(header().exists("Cache-Control"));
        mvc.perform(get("/u/" + owner.handle() + "/card/recent.png")).andExpect(status().isOk());
        mvc.perform(get("/u/" + owner.handle() + "/card/recap.png")).andExpect(status().isOk());
        mvc.perform(get("/u/" + owner.handle().toUpperCase() + "/card/territory.png")).andExpect(status().isOk());
        mvc.perform(get("/u/" + owner.handle() + "/card/selfie.png")).andExpect(status().isNotFound());
        mvc.perform(get("/u/nobody-" + UUID.randomUUID().toString().substring(0, 5))).andExpect(status().isNotFound());

        as(owner, get("/me/cards")).andExpect(jsonPath("$.handle").value(owner.handle()))
            .andExpect(jsonPath("$.profileUrl").value("/u/" + owner.handle()))
            .andExpect(jsonPath("$.cards[1].publicUrl").value("/u/" + owner.handle() + "/card/recent.png"));

        as(owner, put("/me/privacy").contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PRIVATE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.publiclyVisible").value(false));
        mvc.perform(get("/u/" + owner.handle())).andExpect(status().isNotFound())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(owner.handle()))));
        mvc.perform(get("/u/" + owner.handle() + "/card/territory.png")).andExpect(status().isNotFound());
        as(owner, get("/me/cards/territory.png")).andExpect(status().isOk()); // 내 미리보기는 공개 범위와 무관

        as(owner, put("/me/privacy").contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"FRIENDS\"}"))
            .andExpect(jsonPath("$.visibility").value("FRIENDS")).andExpect(jsonPath("$.publiclyVisible").value(false));
        mvc.perform(get("/u/" + owner.handle())).andExpect(status().isNotFound());
        as(owner, put("/me/privacy").contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"everyone\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_VISIBILITY"));
        as(owner, put("/me/privacy").contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PUBLIC\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/u/" + owner.handle())).andExpect(status().isOk());
    }

    @Test
    void VS_카드는_둘_다_공개일_때만() throws Exception {
        Session mine = login(anonymous());
        Session theirs = login(anonymous());
        publish(mine);
        publish(theirs);
        checkIn(mine, "KR-11010", LocalDate.now(clock), "m");
        checkIn(theirs, "KR-11010", LocalDate.now(clock), "t");
        MvcResult result = mvc.perform(get("/u/" + mine.handle() + "/vs/" + theirs.handle() + ".png")).andExpect(status().isOk())
            .andReturn();
        assertThat(png(result).getHeight()).isEqualTo(630);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM share_card WHERE kind = 'VS'", Integer.class)).as("VS 비저장").isZero();
        mvc.perform(get("/u/" + mine.handle() + "/vs/" + mine.handle() + ".png")).andExpect(status().isNotFound());
        as(theirs, put("/me/privacy").contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PRIVATE\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/u/" + mine.handle() + "/vs/" + theirs.handle() + ".png")).andExpect(status().isNotFound());
    }

    // ---- 프로필 링크 합류 · 초대 보상 -----------------------------------------------------------------------------

    @Test
    void 프로필_링크로_처음_합류하면_양쪽이_초대_보상을_받고_같은_쌍은_한_번만() throws Exception {
        Session host = login(anonymous());
        publish(host);
        String mapId = json(as(host, post("/maps").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"부산 원정대\"}"))
            .andExpect(status().isCreated())).get("mapId").asText();
        Anonymous guest = anonymous();
        String joinBody = "{\"mapId\":\"" + mapId + "\"}";

        // 닫힌 지도(PRIVATE 기본)는 프로필로 합류할 수 없다 — 존재 여부를 숨긴 404
        mvc.perform(post("/maps/join-via-profile/" + host.handle()).header(H, guest.token()).contentType(MediaType.APPLICATION_JSON)
            .content(joinBody)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROFILE_MAP_NOT_FOUND"));
        String html = mvc.perform(get("/u/" + host.handle())).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain(mapId);

        as(host, put("/maps/" + mapId + "/settings").contentType(MediaType.APPLICATION_JSON)
            .content("{\"photoRequired\":false,\"dailyCheckInCap\":5,\"visibility\":\"PUBLIC\"}")).andExpect(status().isOk());
        html = mvc.perform(get("/u/" + host.handle())).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("data-join=\"" + mapId + "\"").doesNotContain("inviteCode");

        mvc.perform(post("/maps/join-via-profile/" + host.handle()).header(H, guest.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"mapId\":\"not-a-uuid\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/maps/join-via-profile/nobody").header(H, guest.token()).contentType(MediaType.APPLICATION_JSON)
            .content(joinBody)).andExpect(status().isNotFound());
        mvc.perform(post("/maps/join-via-profile/" + host.handle()).header(H, guest.token()).contentType(MediaType.APPLICATION_JSON)
            .content(joinBody)).andExpect(status().isOk()).andExpect(jsonPath("$.mapId").value(mapId));

        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(owned(guest.id(), "invite:")).containsExactly("invite:guest-ticket");
            assertThat(owned(host.explorerId(), "invite:")).containsExactly("invite:host-flag");
        });
        assertThat(jdbc.queryForObject("SELECT source FROM owned_item WHERE explorer_id = ? AND item_id = 'invite:guest-ticket'",
            String.class, guest.id())).isEqualTo("EVENT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invite_reward WHERE invitee_id = ? AND inviter_id = ?", Integer.class,
            guest.id(), host.explorerId())).isEqualTo(1);

        // 탈퇴 후 재가입(유예 안) — 처음 합류가 아니라 보상 없음, 같은 쌍 기록도 1건 그대로
        mvc.perform(post("/maps/" + mapId + "/leave").header(H, guest.token())).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(1));
        mvc.perform(post("/maps/join-via-profile/" + host.handle()).header(H, guest.token()).contentType(MediaType.APPLICATION_JSON)
            .content(joinBody)).andExpect(status().isOk());
        await().atMost(WAIT).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE published_at IS NULL",
            Integer.class) == 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invite_reward WHERE invitee_id = ?", Integer.class, guest.id()))
            .isEqualTo(1);
        // 지도장 자신은 이미 멤버 — 셀프 초대 불가(409)
        as(host, post("/maps/join-via-profile/" + host.handle()).contentType(MediaType.APPLICATION_JSON).content(joinBody))
            .andExpect(status().isConflict());

        // 프로필을 비공개로 돌리면 지도가 PUBLIC 이어도 프로필 합류는 404, 초대 보상 없음(QA P3-5)
        as(host, put("/me/privacy").contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PRIVATE\"}"))
            .andExpect(status().isOk());
        Anonymous late = anonymous();
        mvc.perform(post("/maps/join-via-profile/" + host.handle()).header(H, late.token()).contentType(MediaType.APPLICATION_JSON)
            .content(joinBody)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROFILE_MAP_NOT_FOUND"));
        assertThat(owned(late.id(), "invite:")).isEmpty();
    }

    @Test
    void 초대코드로_처음_합류해도_지도장과_새_멤버가_초대_보상을_받는다() throws Exception {
        Anonymous owner = anonymous();
        JsonNode map = json(mvc.perform(post("/maps").header(H, owner.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"제주 원정대\"}")).andExpect(status().isCreated()));
        Anonymous friend = anonymous();
        mvc.perform(post("/maps/join").header(H, friend.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"inviteCode\":\"" + map.get("inviteCode").asText() + "\"}")).andExpect(status().isOk());
        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(owned(friend.id(), "invite:")).containsExactly("invite:guest-ticket");
            assertThat(owned(owner.id(), "invite:")).containsExactly("invite:host-flag");
        });
    }

    // ---- QA 회귀 -------------------------------------------------------------------------------------------------

    @Test
    void 병합_직후_그린_카드도_재계산_뒤에는_낡음으로_잡히고_칭호만_바꿔도_낡는다_QA_P2_1() throws Exception {
        Anonymous first = anonymous();
        checkIn(first, "KR-11010", LocalDate.now(clock), null);
        String email = "p21" + UUID.randomUUID().toString().substring(0, 6) + "@example.com";
        Session account = login(first, email);
        awaitSettled();

        Anonymous device = anonymous();
        mvc.perform(post("/dev/seed").header(H, device.token())).andExpect(status().isOk());
        awaitSettled();
        Session merged = login(device, email);
        assertThat(merged.explorerId()).isEqualTo(account.explorerId());
        // 병합 직후(재계산 전) 첫 렌더
        as(merged, get("/me/cards/territory.png")).andExpect(status().isOk());
        Instant firstRender = renderedAt(merged.explorerId(), "TERRITORY");
        awaitSettled();
        as(merged, get("/me/cards")).andExpect(jsonPath("$.cards[0].stale").value(true)); // 예전엔 stale:false 로 굳었다
        clock.advance(Duration.ofMinutes(10));
        as(merged, get("/me/cards/territory.png")).andExpect(status().isOk());
        assertThat(renderedAt(merged.explorerId(), "TERRITORY")).isAfter(firstRender);
        as(merged, get("/me/cards")).andExpect(jsonPath("$.cards[0].stale").value(false));

        // 칭호만 바꿔도(이벤트 없음) 다음 요청에서 낡음
        JsonNode progress = json(as(merged, get("/progress")));
        String other = null;
        for (JsonNode title : progress.get("titles")) {
            if (title.get("earned").asBoolean() && !title.get("selected").asBoolean()) other = title.get("id").asText();
        }
        assertThat(other).as("시드 45곳이면 고를 칭호가 있다").isNotNull();
        as(merged, put("/progress/title").contentType(MediaType.APPLICATION_JSON).content("{\"titleId\":\"" + other + "\"}"))
            .andExpect(status().isOk());
        as(merged, get("/me/cards")).andExpect(jsonPath("$.cards[0].stale").value(true));
    }

    @Test
    void 로그인_직후_공개_카드는_익명_카드가_아니다_handle_이_생기면_TTL_없이_다시_그린다_QA_P2_2() throws Exception {
        Anonymous device = anonymous();
        checkIn(device, "KR-11010", LocalDate.now(clock), null);
        awaitSettled();
        mvc.perform(get("/me/cards/territory.png").header(H, device.token())).andExpect(status().isOk()); // 익명 미리보기
        assertThat(renderedHandle(device.id(), "TERRITORY")).isNull();
        Instant anonymousRender = renderedAt(device.id(), "TERRITORY");

        Session owner = login(device);
        publish(owner);
        clock.advance(Duration.ofSeconds(5)); // 최소 TTL(10분) 안
        mvc.perform(get("/u/" + owner.handle() + "/card/territory.png")).andExpect(status().isOk());
        assertThat(renderedHandle(owner.explorerId(), "TERRITORY")).isEqualTo(owner.handle());
        assertThat(renderedAt(owner.explorerId(), "TERRITORY")).isAfter(anonymousRender);
    }

    @Test
    void 병합하면_익명_탐험가가_받은_초대_보상이_계정으로_옮겨진다_QA_P3_6() throws Exception {
        Session account = login(anonymous());
        Anonymous owner = anonymous();
        JsonNode map = json(mvc.perform(post("/maps").header(H, owner.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"병합 보상\"}")).andExpect(status().isCreated()));
        Anonymous guest = anonymous();
        mvc.perform(post("/maps/join").header(H, guest.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"inviteCode\":\"" + map.get("inviteCode").asText() + "\"}")).andExpect(status().isOk());
        await().atMost(WAIT).until(() -> owned(guest.id(), "invite:").contains("invite:guest-ticket"));
        assertThat(owned(account.explorerId(), "invite:")).isEmpty();

        String email = jdbc.queryForObject("SELECT email FROM account WHERE explorer_id = ?", String.class, account.explorerId());
        Session merged = login(guest, email);
        assertThat(merged.explorerId()).isEqualTo(account.explorerId());
        await().atMost(WAIT).untilAsserted(() ->
            assertThat(owned(account.explorerId(), "invite:")).containsExactly("invite:guest-ticket"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invite_reward WHERE invitee_id = ? AND inviter_id = ?", Integer.class,
            account.explorerId(), owner.id())).isEqualTo(1);
    }

    @Test
    void 예전_MemberJoined_페이로드는_초대자_없이_읽힌다() throws Exception {
        MemberJoined legacy = om.readValue("{\"mapId\":\"m\",\"explorerId\":\"e\",\"role\":\"MEMBER\","
            + "\"joinedAt\":\"2026-10-01T00:00:00Z\",\"rejoined\":false}", MemberJoined.class);
        assertThat(legacy.invitedBy()).isNull();
        assertThat(om.readValue(om.writeValueAsString(new MemberJoined("m", "e", "MEMBER", Instant.EPOCH, false, "h")),
            MemberJoined.class).invitedBy()).isEqualTo("h");
    }

    // ---- 연간 리캡 JSON(06 QA P2-1) -----------------------------------------------------------------------------

    private void checkInOn(Anonymous who, String mapId, String code, LocalDate date) throws Exception {
        clock.advance(Duration.ofSeconds(1));
        mvc.perform(post("/visits").header(H, who.token()).contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(Map.of("regionCode", code, "visitDate", date.toString(), "memo", MEMO, "mapId", mapId))))
            .andExpect(status().isCreated());
    }

    private JsonNode recap(Anonymous who, String query) throws Exception {
        return json(mvc.perform(get("/me/recap" + query).header(H, who.token())).andExpect(status().isOk()));
    }

    @Test
    void 리캡_JSON_은_개인_지도_기준이_기본이고_리캡_카드_PNG_와_같은_값이다() throws Exception {
        Anonymous me = anonymous();
        LocalDate today = LocalDate.now(clock);
        LocalDate lastYear = today.minusYears(1).withMonth(6).withDayOfMonth(1);
        checkIn(me, "KR-11020", today, MEMO);  // 칠한 순서는 중구가 먼저 — 동점은 지역 코드(종로구)로 가른다
        checkIn(me, "KR-11010", today, MEMO);
        checkIn(me, "KR-37430", lastYear, MEMO);

        JsonNode recap = recap(me, "");
        assertThat(recap.get("year").asInt()).isEqualTo(today.getYear());
        assertThat(recap.get("mapId").asText()).isEqualTo(me.personalMapId());
        assertThat(recap.get("newRegions").asInt()).isEqualTo(2);
        assertThat(recap.get("monthCounts")).hasSize(12);
        assertThat(recap.get("monthCounts").get(today.getMonthValue() - 1).asInt()).isEqualTo(2);
        assertThat(recap.get("topProvince").get("provinceCode").asText()).isEqualTo("KR-11");
        assertThat(recap.get("topProvince").get("provinceName").asText()).isEqualTo("서울");
        assertThat(recap.get("topProvince").get("count").asInt()).isEqualTo(2);
        assertThat(recap.get("rarest").get("regionCode").asText()).isEqualTo("KR-11010");
        assertThat(recap.get("rarest").get("rarity").asText()).isEqualTo("COMMON");
        assertThat(recap.get("newProvinces").asInt()).isEqualTo(1); // 서울 — 경북은 작년 방문
        assertThat(recap.get("busiestMonth").get("month").asInt()).isEqualTo(today.getMonthValue());
        assertThat(recap.get("busiestMonth").get("count").asInt()).isEqualTo(2);
        assertThat(recap.get("setsCompleted").asInt()).isZero();
        assertThat(recap.toString()).doesNotContain(MEMO).doesNotContain("photo").doesNotContain(today.toString());

        JsonNode previous = recap(me, "?year=" + lastYear.getYear() + "&mapId=" + me.personalMapId());
        assertThat(previous.get("newRegions").asInt()).isEqualTo(1);
        assertThat(previous.get("rarest").get("regionCode").asText()).isEqualTo("KR-37430");
        assertThat(previous.get("rarest").get("rarity").asText()).isEqualTo("LEGEND");
        assertThat(previous.get("newProvinces").asInt()).isEqualTo(1); // 경북(방문이 모두 작년)
        JsonNode empty = recap(me, "?year=2001");
        assertThat(empty.get("newRegions").asInt()).isZero();
        assertThat(empty.get("topProvince").isNull()).isTrue();
        assertThat(empty.get("rarest").isNull()).isTrue();
        assertThat(empty.get("busiestMonth").isNull()).isTrue();

        // PNG 카드는 같은 계산(PublicVisits.recap) — 카드 내용과 JSON 값이 같다
        CardContent.Recap card = (CardContent.Recap) CardComposer.compose(CardKind.RECAP, showcases.read(me.id(), null),
            Year.of(today.getYear()));
        assertThat(card.newRegions()).isEqualTo(recap.get("newRegions").asInt());
        assertThat(card.monthCounts()).isEqualTo(om.convertValue(recap.get("monthCounts"), List.class));
        assertThat(card.subline()).contains("시·도 " + recap.get("newProvinces").asInt() + "곳 신규");
        assertThat(card.stats()).containsExactly(
            new CardContent.Stat("가장 많이 간 시·도", recap.get("topProvince").get("provinceName").asText() + " "
                + recap.get("topProvince").get("count").asInt() + "곳"),
            new CardContent.Stat("가장 희귀한 곳", "종로구 (일반)"));
        mvc.perform(get("/me/cards/recap.png").header(H, me.token())).andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void 리캡_JSON_은_공유_지도를_고르면_그_지도에서_내가_칠한_곳만_세고_비멤버는_거부한다() throws Exception {
        Anonymous owner = anonymous();
        Anonymous friend = anonymous();
        JsonNode map = json(mvc.perform(post("/maps").header(H, owner.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"리캡 원정대\"}")).andExpect(status().isCreated()));
        String mapId = map.get("mapId").asText();
        String inviteCode = map.get("inviteCode").asText();
        mvc.perform(post("/maps/join").header(H, friend.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"inviteCode\":\"" + inviteCode + "\"}")).andExpect(status().isOk());
        LocalDate today = LocalDate.now(clock);
        checkIn(owner, "KR-11010", today, MEMO);                // 개인 지도
        checkInOn(owner, mapId, "KR-26010", today);             // 공유 지도 — 내 것
        checkInOn(owner, mapId, "KR-26020", today);
        checkInOn(friend, mapId, "KR-37430", today);            // 공유 지도 — 친구 것(내 리캡에 안 셈)

        JsonNode shared = recap(owner, "?mapId=" + mapId);
        assertThat(shared.get("mapId").asText()).isEqualTo(mapId);
        assertThat(shared.get("newRegions").asInt()).isEqualTo(2);
        assertThat(shared.get("topProvince").get("provinceCode").asText()).isEqualTo("KR-26");
        assertThat(shared.get("rarest").get("regionCode").asText()).isEqualTo("KR-26010");
        JsonNode personal = recap(owner, "");
        assertThat(personal.get("newRegions").asInt()).isEqualTo(1);
        assertThat(personal.get("topProvince").get("provinceCode").asText()).isEqualTo("KR-11");
        assertThat(recap(friend, "?mapId=" + mapId).get("rarest").get("rarity").asText()).isEqualTo("LEGEND");

        Anonymous stranger = anonymous();
        mvc.perform(get("/me/recap").param("mapId", mapId).header(H, stranger.token()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
        mvc.perform(get("/me/recap").param("mapId", UUID.randomUUID().toString()).header(H, stranger.token()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MAP_NOT_FOUND"));
        mvc.perform(get("/me/recap").param("year", "0").header(H, owner.token()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_YEAR"));
        mvc.perform(get("/me/recap").param("year", "올해").header(H, owner.token()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_PARAMETER"));
        mvc.perform(get("/me/recap")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("EXPLORER_TOKEN_REQUIRED"));
    }
}
