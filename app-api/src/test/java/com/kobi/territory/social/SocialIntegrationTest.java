package com.kobi.territory.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.outbox.OutboxRelay;
import com.kobi.territory.social.application.FeedProjector;
import com.kobi.territory.social.application.SocialSubscriptions;
import com.kobi.territory.social.domain.friendship.Friendship;
import com.kobi.territory.social.domain.friendship.FriendshipAlreadyExists;
import com.kobi.territory.social.domain.friendship.FriendshipRepository;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 5단계 소셜 D2·D3(+ QA 수정 회귀): 팔로우 규칙(로그인·자기·중복·없는 handle·숨은 대상 404 일관성), 주인 본인 미리보기, 맞팔 시 FRIENDS 프로필·카드·VS 가 열리고 언팔하면 404, 영토 비교 허용 규칙,
 * 이벤트 → 친구 소식 투영(공개 범위·취소 거둠·멱등·재구성), 병합 귀속, 지도 안 랭킹(이의·탈퇴 숨김 제외), 친구 랭킹(탐험가 단위 중복 제거),
 * 상위 % 배치·지역 통계·콜드 스타트.
 * 같은 컨텍스트(H2)를 다른 통합 테스트와 함께 쓰므로 전체 수(모집단)에 기대지 않고 상대적인 값만 본다.
 */
@IntegrationTest
class SocialIntegrationTest {

    private static final String H = "X-Explorer-Token";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired FeedProjector projector;
    @Autowired OutboxRelay relay;
    @Autowired FriendshipRepository friendshipRepository;
    @Autowired PlatformTransactionManager transactionManager;

    record Anonymous(String id, String token, String personalMapId) {}

    record Session(MockHttpSession http, String explorerId, String handle, String personalMapId) {}

    // ---- helpers ---------------------------------------------------------------------------------------------

    private Anonymous anonymous() throws Exception {
        JsonNode body = json(mvc.perform(post("/explorers")).andExpect(status().isCreated()));
        return new Anonymous(body.get("explorerId").asText(), body.get("accessToken").asText(), body.get("personalMapId").asText());
    }

    private Session login() throws Exception {
        return login(null, "social" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
    }

    private Session login(Anonymous device, String email) throws Exception {
        MockHttpServletRequestBuilder request = post("/dev/login").contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(Map.of("email", email)));
        if (device != null) request = request.header(H, device.token());
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        JsonNode body = om.readTree(result.getResponse().getContentAsString());
        return new Session((MockHttpSession) result.getRequest().getSession(false), body.get("explorerId").asText(),
            body.get("handle").asText(), body.get("personalMapId").asText());
    }

    private ResultActions as(Session session, MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder.session(session.http()).with(csrf()));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return om.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private void privacy(Session session, String visibility) throws Exception {
        as(session, put("/me/privacy").contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"" + visibility + "\"}"))
            .andExpect(status().isOk());
    }

    /** 보이는 대상(또는 나를 팔로우하는 대상) 팔로우 → 201. */
    private void follow(Session who, Session whom) throws Exception {
        as(who, post("/friends/" + whom.handle())).andExpect(status().isCreated());
    }

    /** 숨은 대상(PRIVATE·친구 아닌 FRIENDS) 팔로우 → 없는 handle 과 같은 404, 팔로우는 기록된다(QA P3-3). */
    private void followHidden(Session who, Session whom) throws Exception {
        as(who, post("/friends/" + whom.handle())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
    }

    private void checkIn(Session who, String code, String mapId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("regionCode", code, "visitDate", LocalDate.now(clock).toString()));
        if (mapId != null) body.put("mapId", mapId);
        clock.advance(Duration.ofSeconds(1));
        as(who, post("/visits").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
            .andExpect(status().isCreated());
    }

    private void checkIn(Anonymous who, String code) throws Exception {
        clock.advance(Duration.ofSeconds(1));
        mvc.perform(post("/visits").header(H, who.token()).contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(Map.of("regionCode", code, "visitDate", LocalDate.now(clock).toString()))))
            .andExpect(status().isCreated());
    }

    private void awaitSettled() {
        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recalculation_request", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class)).isZero();
        });
    }

    private List<String> feedRegions(Session viewer) throws Exception {
        JsonNode items = json(as(viewer, get("/feed")).andExpect(status().isOk())).get("items");
        List<String> regions = new ArrayList<>();
        items.forEach(item -> {
            if ("VISIT".equals(item.get("kind").asText())) regions.add(item.get("handle").asText() + ":" + item.get("regionCode").asText());
        });
        return regions;
    }

    /** 지금 세대의 그 탐험가 소식 행 수. */
    private int feedRows(String actorId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM feed_entry WHERE actor_id = ? AND generation = "
            + "(SELECT live_generation FROM feed_state WHERE id = 1)", Integer.class, actorId);
    }

    private int liveFeedRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM feed_entry WHERE generation = (SELECT live_generation FROM feed_state WHERE id = 1)",
            Integer.class);
    }

    // ---- 팔로우 ----------------------------------------------------------------------------------------------

    @Test
    void 팔로우는_로그인한_사람만_handle_로_하고_자기_자신_중복은_거절한다() throws Exception {
        Session kim = login();
        Session lee = login();
        Anonymous anonymous = anonymous();
        privacy(lee, "PUBLIC");

        mvc.perform(post("/friends/" + kim.handle()).header(H, anonymous.token())).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
        as(kim, post("/friends/nobody-" + UUID.randomUUID().toString().substring(0, 5))).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
        as(kim, post("/friends/" + kim.handle())).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"));

        as(kim, post("/friends/@" + lee.handle().toUpperCase())).andExpect(status().isCreated())
            .andExpect(jsonPath("$.handle").value(lee.handle())).andExpect(jsonPath("$.following").value(true))
            .andExpect(jsonPath("$.mutual").value(false)).andExpect(jsonPath("$.explorerId").doesNotExist());
        as(kim, post("/friends/" + lee.handle())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_FOLLOWING"));
        as(lee, post("/friends/" + kim.handle())).andExpect(status().isCreated()).andExpect(jsonPath("$.mutual").value(true)); // 나를 팔로우 → 맞팔 가능

        as(kim, get("/friends")).andExpect(status().isOk()).andExpect(jsonPath("$.loggedIn").value(true))
            .andExpect(jsonPath("$.mutualCount").value(1)).andExpect(jsonPath("$.people[0].handle").value(lee.handle()))
            .andExpect(jsonPath("$.people[0].mutual").value(true)).andExpect(jsonPath("$.people[0].explorerId").doesNotExist());
        mvc.perform(get("/friends").header(H, anonymous.token())).andExpect(jsonPath("$.loggedIn").value(false))
            .andExpect(jsonPath("$.people.length()").value(0));

        as(kim, delete("/friends/" + lee.handle())).andExpect(status().isNoContent());
        as(kim, delete("/friends/" + lee.handle())).andExpect(status().isNoContent()); // 멱등
        as(kim, delete("/friends/nobody-zz")).andExpect(status().isNoContent());      // 없는 handle 도 같은 응답(QA P3-3)
        // 이는 김을 한쪽으로 팔로우 중인데 김은 PRIVATE — 숨은 대상이라 이의 목록에서도 빠진다(QA P3-3)
        as(lee, get("/friends")).andExpect(jsonPath("$.mutualCount").value(0)).andExpect(jsonPath("$.people.length()").value(0));
        privacy(kim, "PUBLIC");
        as(lee, get("/friends")).andExpect(jsonPath("$.people[0].following").value(true)).andExpect(jsonPath("$.people[0].follower").value(false));
    }

    @Test
    void 숨은_프로필_팔로우는_없는_handle_과_같은_404이고_상대가_맞팔하면_친구가_된다() throws Exception {
        Session kim = login();
        Session secret = login(); // 기본 PRIVATE
        String unknownBody = as(kim, post("/friends/nobody-" + UUID.randomUUID().toString().substring(0, 5)))
            .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String hiddenBody = as(kim, post("/friends/" + secret.handle())).andExpect(status().isNotFound())
            .andReturn().getResponse().getContentAsString();
        assertThat(hiddenBody).isEqualTo(unknownBody);
        followHidden(kim, secret); // 이미 팔로우 중이어도 409 대신 같은 404
        as(kim, get("/friends")).andExpect(jsonPath("$.people.length()").value(0)); // 목록에도 드러나지 않는다

        // 기록은 됐다 — 상대에겐 "나를 팔로우"로 보이고, 맞팔하면 친구
        as(secret, get("/friends")).andExpect(jsonPath("$.people[0].handle").value(kim.handle()))
            .andExpect(jsonPath("$.people[0].follower").value(true)).andExpect(jsonPath("$.people[0].following").value(false));
        as(secret, post("/friends/" + kim.handle())).andExpect(status().isCreated()).andExpect(jsonPath("$.mutual").value(true));
        as(kim, get("/friends")).andExpect(jsonPath("$.mutualCount").value(1)).andExpect(jsonPath("$.people[0].mutual").value(true));
        // 친구 공개면 맞팔에게 보이므로 이제 존재가 드러나도 된다 → 중복은 409
        privacy(secret, "FRIENDS");
        as(kim, post("/friends/" + secret.handle())).andExpect(status().isConflict());
    }

    // ---- FRIENDS 공개 범위 · 비교 -------------------------------------------------------------------------------

    @Test
    void FRIENDS_프로필은_맞팔로우와_주인에게만_열리고_언팔하면_다시_404다() throws Exception {
        Session owner = login();
        Session friend = login();
        Session stranger = login();
        checkIn(owner, "KR-11010", null);
        checkIn(friend, "KR-11020", null);
        awaitSettled();
        privacy(owner, "FRIENDS");

        as(friend, get("/u/" + owner.handle())).andExpect(status().isNotFound());
        followHidden(friend, owner); // 한쪽 팔로우로는 안 열린다(존재도 숨김)
        as(friend, get("/u/" + owner.handle())).andExpect(status().isNotFound());
        as(friend, get("/compare/" + owner.handle())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));

        follow(owner, friend); // 친구가 나를 팔로우 중 → 맞팔 = 친구
        as(friend, get("/u/" + owner.handle())).andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control")).contains("private"))
            .andExpect(result -> assertThat(result.getResponse().getHeaders("Vary")).contains("Cookie"));
        as(friend, get("/u/" + owner.handle() + "/card/territory.png")).andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control")).contains("private"));
        as(stranger, get("/u/" + owner.handle())).andExpect(status().isNotFound());
        mvc.perform(get("/u/" + owner.handle())).andExpect(status().isNotFound()); // 익명 방문자
        mvc.perform(get("/u/" + owner.handle()).header(H, "not-a-token")).andExpect(status().isNotFound()); // 풀리지 않는 토큰 = 익명

        // 영토 비교: FRIENDS + 맞팔 → 프로필이 보이므로 허용, 나만·둘 다·상대만
        as(friend, get("/compare/" + owner.handle())).andExpect(status().isOk()).andExpect(jsonPath("$.mutual").value(true))
            .andExpect(jsonPath("$.onlyMine[0]").value("KR-11020")).andExpect(jsonPath("$.onlyTheirs[0]").value("KR-11010"))
            .andExpect(jsonPath("$.both.length()").value(0)).andExpect(jsonPath("$.me.regionCount").value(1))
            .andExpect(jsonPath("$.other.handle").value(owner.handle())).andExpect(jsonPath("$.lead").value(0));
        as(friend, get("/compare/" + friend.handle())).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CANNOT_COMPARE_SELF"));

        as(owner, delete("/friends/" + friend.handle())).andExpect(status().isNoContent());
        as(friend, get("/u/" + owner.handle())).andExpect(status().isNotFound());
        as(friend, get("/compare/" + owner.handle())).andExpect(status().isNotFound());

        // 공개 프로필이면 팔로우 없이도 비교 가능
        privacy(owner, "PUBLIC");
        as(stranger, get("/compare/" + owner.handle())).andExpect(status().isOk()).andExpect(jsonPath("$.mutual").value(false));
    }

    @Test
    void 주인_본인은_공개_범위와_무관하게_자기_프로필_카드_VS_를_본다() throws Exception {
        Session owner = login();
        Session other = login();
        checkIn(owner, "KR-11030", null);
        checkIn(other, "KR-11040", null);
        awaitSettled();
        privacy(other, "PUBLIC");
        for (String visibility : List.of("PRIVATE", "FRIENDS")) {
            privacy(owner, visibility);
            as(owner, get("/u/" + owner.handle())).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control")).contains("private"));
            as(owner, get("/u/" + owner.handle() + "/card/territory.png")).andExpect(status().isOk());
            as(owner, get("/u/" + owner.handle() + "/vs/" + other.handle() + ".png")).andExpect(status().isOk());
            as(owner, get("/u/" + other.handle() + "/vs/" + owner.handle() + ".png")).andExpect(status().isOk());
            mvc.perform(get("/u/" + owner.handle())).andExpect(status().isNotFound());           // 방문자에겐 여전히 404
            as(other, get("/u/" + other.handle() + "/vs/" + owner.handle() + ".png")).andExpect(status().isNotFound());
        }
    }

    // ---- 친구 소식 ---------------------------------------------------------------------------------------------

    @Test
    void 친구_소식은_공개_범위를_따르고_취소는_거두며_재구성해도_같다() throws Exception {
        Session me = login();
        Session open = login();        // PUBLIC, 한쪽 팔로우
        Session friendsOnly = login(); // FRIENDS, 맞팔
        Session oneWay = login();      // FRIENDS, 한쪽 팔로우
        Session hidden = login();      // PRIVATE(기본), 맞팔
        privacy(open, "PUBLIC");
        privacy(friendsOnly, "FRIENDS");
        privacy(oneWay, "FRIENDS");
        follow(me, open);
        followHidden(friendsOnly, me); // me 는 PRIVATE — 기록만
        follow(me, friendsOnly);       // friendsOnly 가 나를 팔로우 중 → 맞팔
        followHidden(me, oneWay);
        followHidden(me, hidden);
        follow(hidden, me);            // me 가 hidden 을 팔로우 중 → 맞팔

        checkIn(open, "KR-26010", null);
        checkIn(friendsOnly, "KR-26020", null);
        checkIn(oneWay, "KR-26030", null);
        checkIn(hidden, "KR-26040", null);
        checkIn(open, "KR-26310", null);
        awaitSettled();

        assertThat(feedRegions(me)).containsExactly(open.handle() + ":KR-26310", friendsOnly.handle() + ":KR-26020",
            open.handle() + ":KR-26010");
        JsonNode first = json(as(me, get("/feed"))).get("items").get(0);
        assertThat(first.get("when").asText()).isEqualTo("오늘");
        assertThat(first.has("visitedAt")).isFalse();
        assertThat(first.get("rarity").asText()).isNotBlank();

        // 취소 → 그 소식을 거둔다
        as(open, delete("/visits/KR-26310")).andExpect(status().isNoContent());
        awaitSettled();
        assertThat(feedRegions(me)).containsExactly(friendsOnly.handle() + ":KR-26020", open.handle() + ":KR-26010");

        // 같은 이벤트를 두 번 투영해도 한 행(최소 1회 전달 멱등)
        int before = feedRows(open.explorerId());
        RegionVisited duplicate = new RegionVisited(open.explorerId(), open.personalMapId(), "KR-26010", Rarity.COMMON, "KR-26",
            clock.instant(), LocalDate.now(clock), true, 1, true, 1, List.of(open.explorerId()));
        projector.project(duplicate);
        projector.project(duplicate);
        assertThat(feedRows(open.explorerId())).isEqualTo(before);

        // 읽기 모델 재구성: 다음 세대에 outbox 재생 → 교체, 같은 결과(거둔 소식은 거둔 채), 옛 세대는 지운다
        List<String> beforeRebuild = feedRegions(me);
        int rowsBefore = liveFeedRows();
        int generationBefore = jdbc.queryForObject("SELECT live_generation FROM feed_state WHERE id = 1", Integer.class);
        JsonNode rebuilt = json(mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk()));
        assertThat(rebuilt.get("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(rebuilt.get("replayed").asInt()).isPositive();
        assertThat(rebuilt.get("skipped").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT live_generation FROM feed_state WHERE id = 1", Integer.class)).isEqualTo(generationBefore + 1);
        assertThat(feedRegions(me)).isEqualTo(beforeRebuild);
        assertThat(liveFeedRows()).isEqualTo(rowsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_entry WHERE generation <> (SELECT live_generation FROM feed_state)",
            Integer.class)).as("옛 세대 정리").isZero();
        mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk());
        assertThat(liveFeedRows()).isEqualTo(rowsBefore);

        // 공개 범위를 바꾸면 다시 만들 필요 없이 읽을 때 반영
        privacy(hidden, "FRIENDS");
        assertThat(feedRegions(me)).contains(hidden.handle() + ":KR-26040");
    }

    @Test
    void 운영_재구성은_관리자_토큰으로_비동기로_돌고_상태를_조회한다() throws Exception {
        mvc.perform(post("/admin/rebuild/feed")).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/rebuild/feed").header("X-Admin-Token", "local-admin-token")).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.state").value("RUNNING"));
        await().atMost(WAIT).untilAsserted(() -> mvc.perform(get("/admin/rebuild/feed").header("X-Admin-Token", "local-admin-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("SUCCEEDED")));
        awaitSettled(); // 멈췄던 social.feed 몫이 이어 전달된다
    }

    @Test
    void 병합된_익명_탐험가의_소식은_계정_탐험가의_것이_되고_계정이_취소하면_거둔다() throws Exception {
        String email = "merge" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        Session account = login(null, email);
        Anonymous device = anonymous();
        checkIn(device, "KR-31011");
        checkIn(device, "KR-31012");
        awaitSettled();
        assertThat(feedRows(device.id())).isPositive();

        Session merged = login(device, email);
        assertThat(merged.explorerId()).isEqualTo(account.explorerId());
        awaitSettled();
        assertThat(feedRows(device.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_entry WHERE actor_id = ? AND region_code = 'KR-31011' AND map_id = ?",
            Integer.class, account.explorerId(), account.personalMapId())).as("개인 지도 소식은 계정 개인 지도로").isPositive();

        Session watcher = login();
        privacy(merged, "PUBLIC");
        follow(watcher, merged);
        assertThat(feedRegions(watcher)).contains(merged.handle() + ":KR-31011", merged.handle() + ":KR-31012");

        // 계정이 병합으로 옮겨진 방문을 취소 → 친구 소식에서도 거둔다(QA P2-5), 재구성 뒤에도 같다
        as(merged, delete("/visits/KR-31012")).andExpect(status().isNoContent());
        awaitSettled();
        assertThat(feedRegions(watcher)).contains(merged.handle() + ":KR-31011").doesNotContain(merged.handle() + ":KR-31012");
        mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk());
        assertThat(feedRegions(watcher)).contains(merged.handle() + ":KR-31011").doesNotContain(merged.handle() + ":KR-31012");
    }

    // ---- 랭킹 -----------------------------------------------------------------------------------------------

    @Test
    void 지도_안_랭킹은_이의_방문과_탈퇴_유예_숨김을_빼고_친구_랭킹은_지역을_중복_없이_센다() throws Exception {
        Session owner = login();
        Session guest = login();
        Session leaver = login();
        JsonNode map = json(as(owner, post("/maps").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"소셜 원정대\"}"))
            .andExpect(status().isCreated()));
        String mapId = map.get("mapId").asText();
        String code = map.get("inviteCode").asText();
        for (Session member : List.of(guest, leaver)) {
            as(member, post("/maps/join").contentType(MediaType.APPLICATION_JSON).content("{\"inviteCode\":\"" + code + "\"}"))
                .andExpect(status().isOk());
        }
        checkIn(owner, "KR-37430", mapId);   // 울릉군(전설) 선점
        checkIn(owner, "KR-37011", mapId);
        checkIn(guest, "KR-37430", mapId);
        checkIn(guest, "KR-37020", mapId);   // 선점 — 곧 이의 표시
        checkIn(owner, "KR-37020", mapId);   // 두 번째 — 이의 뒤엔 선점으로 센다(QA Q1)
        checkIn(leaver, "KR-37030", mapId);
        checkIn(leaver, "KR-37040", mapId);
        checkIn(leaver, "KR-37050", mapId);
        checkIn(owner, "KR-37011", null);    // 개인 지도에서도 같은 지역 — 탐험가 단위로는 1곳
        as(owner, put("/maps/" + mapId + "/visits/KR-37020/" + guest.explorerId() + "/dispute")
            .contentType(MediaType.APPLICATION_JSON).content("{\"disputed\":true}")).andExpect(status().isOk());
        as(leaver, post("/maps/" + mapId + "/leave")).andExpect(status().isOk());
        awaitSettled();

        JsonNode ranking = json(as(guest, get("/rankings/maps/" + mapId)).andExpect(status().isOk()));
        assertThat(ranking.get("disputedExcluded").asInt()).isEqualTo(1);
        assertThat(ranking.get("rows")).hasSize(2);
        JsonNode top = ranking.get("rows").get(0);
        assertThat(top.get("handle").asText()).isEqualTo(owner.handle());
        assertThat(top.get("territories").asInt()).isEqualTo(3);
        assertThat(top.get("claims").asInt()).as("이의 표시된 선점의 다음 방문이 선점").isEqualTo(3);
        assertThat(top.get("legends").asInt()).isEqualTo(1);
        JsonNode second = ranking.get("rows").get(1);
        assertThat(second.get("me").asBoolean()).isTrue();
        assertThat(second.get("territories").asInt()).isEqualTo(1);
        assertThat(second.get("claims").asInt()).isZero();
        assertThat(second.get("rank").asInt()).isEqualTo(2);
        as(leaver, get("/rankings/maps/" + mapId)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));

        privacy(owner, "FRIENDS");
        followHidden(guest, owner);
        follow(owner, guest);
        JsonNode friends = json(as(guest, get("/rankings/friends")).andExpect(status().isOk()));
        assertThat(friends.get("friendCount").asInt()).isEqualTo(1);
        assertThat(friends.get("baseline").isNull()).isTrue();
        JsonNode ownerRow = friends.get("rows").get(0);
        JsonNode myRow = friends.get("rows").get(1);
        assertThat(ownerRow.get("handle").asText()).isEqualTo(owner.handle());
        assertThat(ownerRow.get("regionCount").asInt()).as("공유·개인 지도의 KR-37011 은 1곳").isEqualTo(3);
        assertThat(ownerRow.get("rank").asInt()).isEqualTo(1);
        assertThat(myRow.get("me").asBoolean()).isTrue();
        assertThat(myRow.get("regionCount").asInt()).as("이의 표시는 탐험가 단위 집계에 영향 없음").isEqualTo(2);

        // PRIVATE 인 맞팔 친구는 친구 랭킹·비교에서 숨김(리더 결정 1) — 친구 0명이면 콜드 스타트로
        privacy(owner, "PRIVATE");
        JsonNode hiddenFriend = json(as(guest, get("/rankings/friends")).andExpect(status().isOk()));
        assertThat(hiddenFriend.get("friendCount").asInt()).isZero();
        assertThat(hiddenFriend.get("rows")).hasSize(1);
        assertThat(hiddenFriend.get("rows").get(0).get("me").asBoolean()).isTrue();
        as(guest, get("/compare/" + owner.handle())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
    }

    @Test
    void 상위_퍼센트_배치는_지역이_있는_사람만_모집단이고_친구가_없으면_지역_평균과_비교한다() throws Exception {
        Session busy = login();
        Session lazy = login();
        Session none = login();
        for (String code : List.of("KR-38050", "KR-38060", "KR-38030")) checkIn(busy, code, null);
        checkIn(lazy, "KR-38050", null);
        awaitSettled();

        as(busy, get("/rankings/me/percentile")).andExpect(jsonPath("$.computed").value(false));
        JsonNode batch = json(mvc.perform(post("/dev/batch/rank")).andExpect(status().isOk()));
        int withRegions = jdbc.queryForObject("SELECT COUNT(DISTINCT r.explorer_id) FROM explorer_region r JOIN explorer e "
            + "ON e.id = r.explorer_id WHERE r.active_map_count > 0 AND e.status = 'ACTIVE'", Integer.class);
        assertThat(batch.get("population").asInt()).as("모집단 = 활성 지역 1곳 이상인 활성 탐험가(리더 결정 5)").isEqualTo(withRegions);

        JsonNode high = json(as(busy, get("/rankings/me/percentile")).andExpect(status().isOk()));
        JsonNode low = json(as(lazy, get("/rankings/me/percentile")).andExpect(status().isOk()));
        assertThat(high.get("computed").asBoolean()).isTrue();
        assertThat(high.get("regionCount").asInt()).isEqualTo(3);
        assertThat(high.get("rank").asInt()).isLessThan(low.get("rank").asInt());
        assertThat(high.get("topPercent").asInt()).isLessThanOrEqualTo(low.get("topPercent").asInt()).isBetween(1, 100);
        assertThat(high.get("population").asInt()).isEqualTo(batch.get("population").asInt());
        as(none, get("/rankings/me/percentile")).andExpect(jsonPath("$.computed").value(false)); // 지역 0곳

        JsonNode stats = json(mvc.perform(get("/catalog/region-stats")).andExpect(status().isOk()));
        assertThat(stats.get("population").asInt()).isEqualTo(batch.get("population").asInt());
        JsonNode gyeongnam = null;
        for (JsonNode region : stats.get("regions")) if ("KR-38050".equals(region.get("regionCode").asText())) gyeongnam = region;
        assertThat(gyeongnam).isNotNull();
        assertThat(gyeongnam.get("visitorCount").asInt()).isGreaterThanOrEqualTo(2);

        // 콜드 스타트: 친구 0명 → 주 활동 시·도(경남) 평균 유저
        JsonNode cold = json(as(busy, get("/rankings/friends")).andExpect(status().isOk()));
        assertThat(cold.get("friendCount").asInt()).isZero();
        assertThat(cold.get("mainProvince").asText()).isEqualTo("KR-38");
        assertThat(cold.get("baseline").get("provinceCode").asText()).isEqualTo("KR-38");
        assertThat(cold.get("baseline").get("explorerCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(cold.get("rows")).hasSize(1);
        // 지역 0곳이면 전국 평균
        JsonNode nationwide = json(as(none, get("/rankings/friends")).andExpect(status().isOk()));
        assertThat(nationwide.get("baseline").get("nationwide").asBoolean()).isTrue();
        // 익명도 본인 줄과 비교는 본다(팔로우는 로그인 뒤)
        Anonymous anonymous = anonymous();
        mvc.perform(get("/rankings/friends").header(H, anonymous.token())).andExpect(status().isOk())
            .andExpect(jsonPath("$.loggedIn").value(false)).andExpect(jsonPath("$.rows[0].me").value(true));
    }

    @Test
    void 지역별_방문자_수는_병합돼_비활성인_탐험가를_세지_않는다() throws Exception {
        String email = "stats" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        Session account = login(null, email);
        checkIn(account, "KR-35011", null);
        Anonymous device = anonymous();
        checkIn(device, "KR-35011");
        checkIn(device, "KR-35012");
        awaitSettled();
        login(device, email); // 병합 — 익명의 explorer_region 은 복구용으로 남는다
        awaitSettled();

        mvc.perform(post("/dev/batch/rank")).andExpect(status().isOk());
        JsonNode stats = json(mvc.perform(get("/catalog/region-stats")).andExpect(status().isOk()));
        for (JsonNode region : stats.get("regions")) {
            String code = region.get("regionCode").asText();
            if (List.of("KR-35011", "KR-35012").contains(code)) {
                // 같은 DB 를 쓰는 다른 테스트가 같은 지역을 칠했을 수 있다 — 기대값은 "활성 탐험가만" 센 수(병합된 익명 행 제외)
                int activeVisitors = jdbc.queryForObject("SELECT COUNT(*) FROM explorer_region r JOIN explorer e ON e.id = r.explorer_id "
                    + "WHERE r.region_code = ? AND r.active_map_count > 0 AND e.status = 'ACTIVE'", Integer.class, code);
                int allRows = jdbc.queryForObject("SELECT COUNT(*) FROM explorer_region WHERE region_code = ? AND active_map_count > 0",
                    Integer.class, code);
                assertThat(allRows).as("병합된 익명의 행이 남아 있다(전제)").isGreaterThan(activeVisitors);
                assertThat(region.get("visitorCount").asInt()).as(code).isEqualTo(activeVisitors);
            }
            assertThat(region.get("visitorCount").asInt()).isLessThanOrEqualTo(stats.get("population").asInt());
        }
    }

    // ---- QA r2 ------------------------------------------------------------------------------------------------

    /** 내가 관측할 수 있는 값들(응답 본문 그대로) — 숨은 팔로우 전후·없는 handle 팔로우 전후가 같아야 한다. */
    private List<String> observables(Session who) throws Exception {
        List<String> out = new ArrayList<>();
        for (String path : List.of("/feed", "/friends", "/rankings/friends")) {
            out.add(as(who, get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        }
        return out;
    }

    @Test
    void 숨은_handle_과_없는_handle_은_응답도_이후_관측값도_같다() throws Exception {
        Session hidden = login();       // PRIVATE(기본)
        Session prober = login();
        Session sameAsProber = login(); // 대조군: 같은 상태에서 없는 handle 만 팔로우
        String missing = "nobody-" + UUID.randomUUID().toString().substring(0, 5);

        List<String> before = observables(prober);
        MvcResult hiddenResult = as(prober, post("/friends/" + hidden.handle())).andReturn();
        MvcResult missingResult = as(sameAsProber, post("/friends/" + missing)).andReturn();
        assertThat(hiddenResult.getResponse().getStatus()).isEqualTo(404).isEqualTo(missingResult.getResponse().getStatus());
        assertThat(hiddenResult.getResponse().getContentAsString()).isEqualTo(missingResult.getResponse().getContentAsString());
        assertThat(hiddenResult.getResponse().getHeaderNames()).isEqualTo(missingResult.getResponse().getHeaderNames());

        // 이후 관측값: 숨은 팔로우는 followingCount·목록·랭킹 어디에도 차이를 만들지 않는다
        List<String> afterHidden = observables(prober);
        assertThat(afterHidden).isEqualTo(before);
        assertThat(json(as(prober, get("/feed"))).get("followingCount").asInt()).isZero();
        as(prober, post("/friends/" + missing)).andExpect(status().isNotFound());
        assertThat(observables(prober)).isEqualTo(before);

        // 익명은 handle 을 찾기 전에 401 — 있는 handle 도 없는 handle 도 같다
        Anonymous anonymous = anonymous();
        String anonHidden = mvc.perform(post("/friends/" + hidden.handle()).header(H, anonymous.token()))
            .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String anonMissing = mvc.perform(post("/friends/" + missing).header(H, anonymous.token()))
            .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(anonHidden).isEqualTo(anonMissing).contains("LOGIN_REQUIRED");

        // 동시 요청도 숨은 대상은 전부 같은 404(PK 경합 → 409 로 새지 않는다)
        Session racer = login();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) calls.add(() -> as(racer, post("/friends/" + hidden.handle())).andReturn().getResponse().getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : pool.invokeAll(calls)) statuses.add(future.get());
            assertThat(statuses).containsOnly(404);
        } finally {
            pool.shutdownNow();
        }
        // 저장 경합 번역: 이미 있는 쌍을 다시 넣으면 FriendshipAlreadyExists(서비스가 숨은 대상이면 404 로 바꾼다)
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> friendshipRepository.add(
            Friendship.restore(ExplorerId.of(racer.explorerId()), ExplorerId.of(hidden.explorerId()), Instant.now()))))
            .isInstanceOf(FriendshipAlreadyExists.class);

        // 의도된 효과는 유지: 대상에겐 "나를 팔로우"로 보이고 맞팔하면 친구
        as(hidden, get("/friends")).andExpect(jsonPath("$.people.length()").value(2));
    }

    @Test
    void 재구성_중_체크인_취소_재체크인이_겹쳐도_재체크인_소식은_남고_재구성을_반복해도_같다() throws Exception {
        Session viewer = login();
        Session actor = login();
        privacy(actor, "PUBLIC");
        follow(viewer, actor);
        awaitSettled();
        // 재구성 동안처럼 social.feed 몫만 멈춘 상태에서 체크인 → 취소 → 재체크인(릴레이는 재구성 끝난 뒤 대기분을 다시 보낸다)
        relay.pauseSubscriber(SocialSubscriptions.FEED_SUBSCRIBER);
        try {
            checkIn(actor, "KR-39010", null);
            clock.advance(Duration.ofSeconds(1));
            as(actor, delete("/visits/KR-39010")).andExpect(status().isNoContent());
            checkIn(actor, "KR-39010", null);
            await().atMost(WAIT).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_delivery d JOIN outbox o ON o.id = d.event_id WHERE o.aggregate_id = ? "
                    + "AND d.subscriber = 'progression.progress' AND d.status = 'DELIVERED'", Integer.class, actor.personalMapId()))
                .isGreaterThanOrEqualTo(3));
            JsonNode rebuilt = json(mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk()));
            assertThat(rebuilt.get("state").asText()).isEqualTo("SUCCEEDED");
        } finally {
            relay.resumeSubscriber(SocialSubscriptions.FEED_SUBSCRIBER);
        }
        awaitSettled(); // 대기분(체크인 #1·취소·재체크인 #2) 재전달 — 취소는 자기 이전·회차 이하만 거둔다
        assertThat(feedRegions(viewer)).contains(actor.handle() + ":KR-39010");
        List<String> once = feedRegions(viewer);
        mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk());
        assertThat(feedRegions(viewer)).isEqualTo(once);
        mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk());
        assertThat(feedRegions(viewer)).isEqualTo(once);
    }

    @Test
    void 재생_중_처리_실패가_있으면_FAILED_로_이전_세대를_유지하고_읽기_불가_행은_막지_않는다() throws Exception {
        Session viewer = login();
        Session actor = login();
        privacy(actor, "PUBLIC");
        follow(viewer, actor);
        checkIn(actor, "KR-39020", null);
        awaitSettled();
        List<String> before = feedRegions(viewer);
        int generationBefore = jdbc.queryForObject("SELECT live_generation FROM feed_state WHERE id = 1", Integer.class);
        // 릴레이가 보지 않도록 이미 발행된 행으로 넣는다(재생은 발행 여부와 무관하게 전부 읽는다)
        String poison = "{\"explorerId\":\"not-a-uuid\",\"mapId\":\"m\",\"regionCode\":\"KR-39020\",\"rarity\":\"COMMON\","
            + "\"provinceCode\":\"KR-39\",\"visitedAt\":\"2026-10-02T03:00:00Z\",\"visitDate\":\"2026-10-02\",\"isFirstInProvince\":false,"
            + "\"nth\":1,\"isFirstClaim\":false,\"visitGeneration\":1,\"memberIds\":null}";
        jdbc.update("INSERT INTO outbox (aggregate, aggregate_id, event_type, payload, created_at, published_at) VALUES "
            + "('Territory', 'poison', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", RegionVisited.class.getName(), poison);
        jdbc.update("INSERT INTO outbox (aggregate, aggregate_id, event_type, payload, created_at, published_at) VALUES "
            + "('Territory', 'legacy', 'com.example.RemovedEvent', '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        try {
            JsonNode failed = json(mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk()));
            assertThat(failed.get("state").asText()).isEqualTo("FAILED");
            assertThat(failed.get("skipped").asInt()).isEqualTo(1);
            assertThat(failed.get("unreadable").asInt()).isGreaterThanOrEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT live_generation FROM feed_state WHERE id = 1", Integer.class)).isEqualTo(generationBefore);
            assertThat(feedRegions(viewer)).isEqualTo(before);
        } finally {
            jdbc.update("DELETE FROM outbox WHERE aggregate_id IN ('poison', 'legacy')");
        }
        JsonNode ok = json(mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk()));
        assertThat(ok.get("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(feedRegions(viewer)).isEqualTo(before);
    }
}
