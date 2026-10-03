package com.kobi.territory.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.event.MemberPurged;
import com.kobi.territory.outbox.OutboxRelay;
import com.kobi.territory.wardrobe.application.InventoryRecalculateService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kobi.territory.exploration.api.event.ExplorerMerged;
import com.kobi.territory.exploration.api.event.MemberReassigned;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.exploration.application.AccountService;
import com.kobi.territory.exploration.application.ExplorerMergeService;
import com.kobi.territory.exploration.domain.explorer.AccountIdentity;
import com.kobi.territory.exploration.domain.explorer.LoginPlan;
import com.kobi.territory.progression.application.RecalculateService;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 4단계 파트 A D2·D3: /dev/login(실제 OIDC 성공과 같은 경로) → 계정 연결/병합 → 이벤트 연쇄(ExplorerMerged → VisitsMerged →
 * 정리, MembershipHandover → MemberJoined·MemberLeft) → 재계산 예약으로 진행·인벤토리 재계산, 세션·토큰 인증 공존, CSRF, handle,
 * 구글 미설정 상태, oidcLogin() 포스트프로세서.
 */
@IntegrationTest
class AccountLoginIntegrationTest {

    private static final String H = "X-Explorer-Token";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accounts;
    @Autowired ExplorerMergeService merges;
    @Autowired ExplorerProfileQuery profiles;
    @Autowired RecalculateService recalculate;
    @Autowired InventoryRecalculateService inventoryRecalculate;
    @Autowired OutboxRelay relay;
    @Autowired EventOutbox outbox;
    @Autowired PlatformTransactionManager transactionManager;

    record Anonymous(String id, String token, String personalMapId) {}

    /** 로그인 세션 + 응답 본문. */
    record Session(MockHttpSession http, JsonNode login) {
        String explorerId() { return login.get("explorerId").asText(); }
    }

    private Anonymous anonymous() throws Exception {
        JsonNode body = json(mvc.perform(post("/explorers")).andExpect(status().isCreated()));
        return new Anonymous(body.get("explorerId").asText(), body.get("accessToken").asText(), body.get("personalMapId").asText());
    }

    private JsonNode json(ResultActions result) throws Exception {
        return om.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    private Session devLogin(String email, String sub, Anonymous device) throws Exception {
        MockHttpServletRequestBuilder request = post("/dev/login").contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(sub == null ? Map.of("email", email) : Map.of("email", email, "sub", sub)));
        if (device != null) request.header(H, device.token());
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false),
            om.readTree(result.getResponse().getContentAsString()));
    }

    private ResultActions checkIn(Anonymous who, String mapId, String code, LocalDate date) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("regionCode", code, "visitDate", date.toString()));
        if (mapId != null) body.put("mapId", mapId);
        clock.advance(Duration.ofSeconds(1));
        return mvc.perform(post("/visits").header(H, who.token()).contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(body)));
    }

    /**
     * 세션 + CSRF(쿠키 XSRF-TOKEN 값을 헤더 X-XSRF-TOKEN 으로 — 화면과 같은 방식). spring-security-test 의 csrf() 는 쓰지 않는다:
     * 공유 CsrfFilter 의 저장소를 세션 저장소로 바꿔 놓아 같은 컨텍스트의 다른 테스트가 쿠키를 못 받게 된다.
     */
    private ResultActions asSession(Session session, MockHttpServletRequestBuilder builder) throws Exception {
        Cookie xsrf = xsrfCookie();
        return mvc.perform(builder.session(session.http()).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()));
    }

    private Cookie xsrfCookie() throws Exception {
        Cookie xsrf = mvc.perform(get("/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).as("XSRF-TOKEN 쿠키").isNotNull();
        return xsrf;
    }

    private List<String> visitsOf(String mapId) {
        return jdbc.queryForList("SELECT CONCAT(region_code, '@', visit_date, '@', checked_in_by) FROM visit WHERE map_id = ? "
            + "ORDER BY region_code", String.class, mapId);
    }

    private void awaitSettled(String explorerId) {
        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recalculation_request WHERE explorer_id = ?", Integer.class,
                explorerId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class)).isZero();
        });
    }

    // ---- 구글 미설정 ----------------------------------------------------------------------------------------------

    @Test
    void 구글_클라이언트_ID_가_없어도_기동하고_로그인만_비활성이다() throws Exception {
        mvc.perform(get("/auth/session")).andExpect(status().isOk())
            .andExpect(jsonPath("$.googleLoginEnabled").value(false))
            .andExpect(jsonPath("$.loginUrl").doesNotExist())
            .andExpect(jsonPath("$.loggedIn").value(false));
        mvc.perform(get("/oauth2/authorization/google")).andExpect(status().isNotFound());
        mvc.perform(post("/auth/login-intent")).andExpect(status().isOk())
            .andExpect(jsonPath("$.googleLoginEnabled").value(false));
    }

    // ---- 계정 연결 ------------------------------------------------------------------------------------------------

    @Test
    void 처음_로그인하면_지금_익명_탐험가를_계정에_연결한다_영토_유지_토큰_무효() throws Exception {
        Anonymous device = anonymous();
        checkIn(device, null, "KR-11010", LocalDate.now(clock)).andExpect(status().isCreated());
        String email = unique("Choi.") + "+x@example.com";

        Session session = devLogin(email, null, device);
        assertThat(session.login().get("outcome").asText()).isEqualTo("LINKED");
        assertThat(session.explorerId()).isEqualTo(device.id());
        String handle = session.login().get("handle").asText();
        assertThat(handle).matches("explorer-[a-z2-9]{4,6}").doesNotContain("choi"); // 이메일과 무관한 랜덤(사용자 결정 Q1)
        assertThat(session.login().get("merge").isNull()).isTrue();

        // 세션으로 영토(토큰 헤더 없이)
        mvc.perform(get("/territory").session(session.http())).andExpect(status().isOk())
            .andExpect(jsonPath("$.visits[0].regionCode").value("KR-11010"));
        mvc.perform(get("/explorers/me").session(session.http())).andExpect(status().isOk())
            .andExpect(jsonPath("$.anonymous").value(false)).andExpect(jsonPath("$.handle").value(handle));
        // 익명 토큰은 무효(로그아웃 뒤 남의 기기가 계정 기록을 보지 않게)
        mvc.perform(get("/territory").header(H, device.token())).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("EXPLORER_TOKEN_INVALID"));
        // 세션 조회 — 안내는 한 번만
        mvc.perform(get("/auth/session").session(session.http())).andExpect(jsonPath("$.loggedIn").value(true))
            .andExpect(jsonPath("$.handle").value(handle)).andExpect(jsonPath("$.mergeNotice.outcome").value("LINKED"));
        mvc.perform(get("/auth/session").session(session.http())).andExpect(jsonPath("$.mergeNotice").doesNotExist());
        // 공개 Query(파트 B 용)
        assertThat(profiles.explorerIdByHandle(" @" + handle.toUpperCase() + " ")).contains(device.id());
        assertThat(profiles.handleOf(device.id())).contains(handle);
        assertThat(profiles.accountLinked(device.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE event_type LIKE '%HandleChanged' AND aggregate_id = ?",
            Integer.class, device.id())).isEqualTo(1);

        // 같은 계정으로 다시 로그인(토큰 없음) → 로그인만
        Session again = devLogin(email, null, null);
        assertThat(again.login().get("outcome").asText()).isEqualTo("SIGNED_IN");
        assertThat(again.explorerId()).isEqualTo(device.id());
    }

    @Test
    void 토큰_없이_처음_로그인하면_새_탐험가를_만든다_같은_이메일_로컬파트여도_handle_은_서로_다른_랜덤() throws Exception {
        String local = unique("dup");
        Session first = devLogin(local + "@a.example", null, null);
        Session second = devLogin(local + "@b.example", null, null);
        assertThat(first.login().get("outcome").asText()).isEqualTo("CREATED");
        String firstHandle = first.login().get("handle").asText();
        assertThat(firstHandle).startsWith("explorer-").doesNotContain(local);
        assertThat(second.login().get("handle").asText()).startsWith("explorer-").isNotEqualTo(firstHandle);
        assertThat(profiles.handleOf(first.explorerId())).contains(firstHandle);
    }

    // ---- 병합 -----------------------------------------------------------------------------------------------------

    @Test
    void 다른_기기의_익명_기록을_기존_계정으로_병합한다_이벤트_연쇄와_재계산() throws Exception {
        // 계정 탐험가 B: 종로(2026-09-01)
        Anonymous first = anonymous();
        checkIn(first, null, "KR-11010", LocalDate.of(2026, 9, 1)).andExpect(status().isCreated());
        String email = unique("merge") + "@example.com";
        Session account = devLogin(email, null, first);
        String accountId = account.explorerId();
        awaitSettled(accountId);
        long xpBefore = json(mvc.perform(get("/progress").session(account.http()))).get("xp").asLong();

        // 두 번째 기기 A: 종로(더 이른 2025-01-01, 메모) + 가평(새 지역) + A 가 지도장인 공유 지도(C 멤버)
        Anonymous device = anonymous();
        mvc.perform(post("/visits").header(H, device.token()).contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(Map.of("regionCode", "KR-11010", "visitDate", "2025-01-01", "memo", "익명메모"))))
            .andExpect(status().isCreated());
        checkIn(device, null, "KR-31370", LocalDate.now(clock)).andExpect(status().isCreated());
        JsonNode map = json(mvc.perform(post("/maps").header(H, device.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"병합 원정대\"}")).andExpect(status().isCreated()));
        String sharedMapId = map.get("mapId").asText();
        Anonymous friend = anonymous();
        mvc.perform(post("/maps/join").header(H, friend.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"inviteCode\":\"" + map.get("inviteCode").asText() + "\"}")).andExpect(status().isOk());
        checkIn(device, sharedMapId, "KR-26010", LocalDate.now(clock)).andExpect(status().isCreated());
        awaitSettled(device.id());

        // 같은 계정으로 로그인 → 병합(사용자 확정: 기존 계정으로)
        Session merged = devLogin(email, null, device);
        assertThat(merged.login().get("outcome").asText()).isEqualTo("MERGED");
        assertThat(merged.explorerId()).isEqualTo(accountId);
        assertThat(merged.login().get("merge").get("fromExplorerId").asText()).isEqualTo(device.id());
        assertThat(merged.login().get("merge").get("movedRegions").asInt()).isEqualTo(2);
        assertThat(merged.login().get("merge").get("newRegions").asInt()).isEqualTo(1);
        // 로그인 트랜잭션은 A(Explorer) 하나만: MERGED + 이벤트 적재
        assertThat(jdbc.queryForObject("SELECT status FROM explorer WHERE id = ?", String.class, device.id())).isEqualTo("MERGED");
        assertThat(jdbc.queryForObject("SELECT access_token_hash FROM explorer WHERE id = ?", String.class, device.id())).isNull();
        mvc.perform(get("/territory").header(H, device.token())).andExpect(status().isUnauthorized());

        // 연쇄 결과: B 개인 지도 = 종로(익명 쪽 이른 날짜·메모) + 가평, A 개인 지도는 비움
        awaitSettled(accountId);
        assertThat(visitsOf(account.login().get("personalMapId").asText()))
            .containsExactly("KR-11010@2025-01-01@" + accountId, "KR-31370@" + LocalDate.now(clock) + "@" + accountId);
        assertThat(jdbc.queryForObject("SELECT memo FROM visit WHERE map_id = ? AND region_code = 'KR-11010'", String.class,
            account.login().get("personalMapId").asText())).isEqualTo("익명메모");
        assertThat(visitsOf(device.personalMapId())).isEmpty();
        // 공유 지도(사용자 결정 Q2): B 가 A 자리(지도장)를 잇고 A 는 탈퇴 기록 없이 빠짐, A 방문(선점)은 B 것으로 재귀속
        assertThat(jdbc.queryForObject("SELECT owner_id FROM expedition_map WHERE id = ?", String.class, sharedMapId))
            .isEqualTo(accountId);
        assertThat(jdbc.queryForList("SELECT CONCAT(explorer_id, ':', role, ':', CASE WHEN left_at IS NULL THEN 'TRUE' ELSE 'FALSE' END) "
            + "FROM map_member WHERE map_id = ?", String.class, sharedMapId))
            .containsExactlyInAnyOrder(accountId + ":OWNER:TRUE", friend.id() + ":MEMBER:TRUE");
        assertThat(visitsOf(sharedMapId)).containsExactly("KR-26010@" + LocalDate.now(clock) + "@" + accountId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ? AND hidden_at IS NOT NULL", Integer.class,
            sharedMapId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%MemberLeft'",
            Integer.class, sharedMapId)).isZero();
        mvc.perform(get("/maps").session(merged.http())).andExpect(status().isOk())
            .andExpect(jsonPath("$[1].mapId").value(sharedMapId));

        // 진행·인벤토리는 재계산 예약으로: 가평 기본 XP·시·도 첫 발, 지역 아이템
        JsonNode progress = json(mvc.perform(get("/progress").session(merged.http())));
        assertThat(progress.get("xp").asLong()).isGreaterThan(xpBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE ref_id = ?", Integer.class,
            "region:" + accountId + ":KR-31370#1")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM owned_item WHERE explorer_id = ? AND item_id = 'region:KR-31370'",
            Integer.class, accountId)).isEqualTo(1);
        // 재계산을 다시 돌려도 같다(이벤트 누적 = 재계산)
        assertThat(recalculate.recalculateIfSettled(ExplorerId.of(accountId))).isTrue();
        assertThat(json(mvc.perform(get("/progress").session(merged.http()))).get("xp").asLong())
            .isEqualTo(progress.get("xp").asLong());

        // 멱등: 같은 병합 이벤트를 다시 처리해도 그대로, 같은 기기로 다시 로그인하면 로그인만
        merges.onExplorerMerged(new ExplorerMerged(device.id(), accountId, device.personalMapId(),
            account.login().get("personalMapId").asText(), clock.instant()));
        assertThat(visitsOf(account.login().get("personalMapId").asText())).hasSize(2);
        AccountService.LoginOutcome replay = accounts.login(new AccountIdentity("google", "dev:" + email, email),
            ExplorerId.of(device.id()));
        assertThat(replay.kind()).isEqualTo(LoginPlan.Kind.SIGN_IN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE event_type LIKE '%ExplorerMerged' AND aggregate_id = ?",
            Integer.class, accountId)).isEqualTo(1);
        // 병합된 탐험가는 재계산 전체 대상에서 빠진다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM explorer WHERE status = 'ACTIVE' AND id = ?", Integer.class,
            device.id())).isZero();

    }

    // ---- 공유 지도 재귀속(사용자 결정 Q2) — 하류 반영 ----------------------------------------------------------------

    private long xpOf(String explorerId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM xp_ledger WHERE explorer_id = ?", Long.class, explorerId);
    }

    private boolean ledgerHas(String explorerId, String refId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id = ?", Integer.class,
            explorerId, refId) > 0;
    }

    private List<String> claimsOf(String mapId) throws Exception {
        return jdbc.queryForList("SELECT CONCAT(region_code, ':', checked_in_by) FROM visit v WHERE map_id = ? AND hidden_at IS NULL "
            + "AND NOT EXISTS (SELECT 1 FROM visit o WHERE o.map_id = v.map_id AND o.region_code = v.region_code AND o.hidden_at IS NULL "
            + "AND (o.claim_rank_at < v.claim_rank_at OR (o.claim_rank_at = v.claim_rank_at AND o.visited_at < v.visited_at))) "
            + "ORDER BY region_code", String.class, mapId);
    }

    @Test
    void 병합하면_공유_지도_방문과_선점이_선점_순서_그대로_계정으로_간다_다른_멤버의_선점_XP_는_그대로() throws Exception {
        Anonymous owner = anonymous();
        JsonNode map = json(mvc.perform(post("/maps").header(H, owner.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"재귀속 원정대\"}")).andExpect(status().isCreated()));
        String mapId = map.get("mapId").asText();
        String invite = "{\"inviteCode\":\"" + map.get("inviteCode").asText() + "\"}";
        // 계정 탐험가 B 도 이 지도 멤버 — 같은 지역 충돌 규칙 확인용
        Anonymous accountDevice = anonymous();
        String email = unique("reassign") + "@example.com";
        Session account = devLogin(email, null, accountDevice);
        String accountId = account.explorerId();
        asSession(account, post("/maps/join").contentType(MediaType.APPLICATION_JSON).content(invite)).andExpect(status().isOk());
        Anonymous device = anonymous();
        mvc.perform(post("/maps/join").header(H, device.token()).contentType(MediaType.APPLICATION_JSON).content(invite))
            .andExpect(status().isOk());

        // KR-26010: 익명 A 선점 → 지도장 O. KR-26020: A 혼자. KR-26030: B 선점 → A. KR-26040: A 선점 → B(방문일은 B 가 더 이름)
        checkIn(device, mapId, "KR-26010", LocalDate.now(clock)).andExpect(status().isCreated());
        checkIn(owner, mapId, "KR-26010", LocalDate.now(clock)).andExpect(status().isCreated());
        checkIn(device, mapId, "KR-26020", LocalDate.now(clock)).andExpect(status().isCreated());
        clock.advance(Duration.ofSeconds(1));
        asSession(account, post("/visits").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(
            Map.of("regionCode", "KR-26030", "visitDate", LocalDate.now(clock).toString(), "mapId", mapId)))).andExpect(status().isCreated());
        checkIn(device, mapId, "KR-26030", LocalDate.now(clock)).andExpect(status().isCreated());
        mvc.perform(post("/visits").header(H, device.token()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(
            Map.of("regionCode", "KR-26040", "visitDate", LocalDate.now(clock).toString(), "memo", "익명 선점", "mapId", mapId))))
            .andExpect(status().isCreated());
        clock.advance(Duration.ofSeconds(1));
        asSession(account, post("/visits").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(
            Map.of("regionCode", "KR-26040", "visitDate", "2020-01-01", "mapId", mapId)))).andExpect(status().isCreated());
        awaitSettled(device.id());
        awaitSettled(accountId);
        long ownerXp = xpOf(owner.id());
        List<String> setProgressBefore = jdbc.queryForList(
            "SELECT CONCAT(set_id, ':', collected_codes) FROM set_progress WHERE map_id = ? ORDER BY set_id", String.class, mapId);
        long claimTransfersBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%ClaimTransferred'", Long.class, mapId);

        Session merged = devLogin(email, null, device);
        assertThat(merged.login().get("outcome").asText()).isEqualTo("MERGED");
        awaitSettled(accountId);

        // 영토: A 방문은 B 것(26010·26020 그대로, 26040 은 A 쪽 선점이 앞서 A 기록), 26030 은 B 자기 방문이 앞서 B 것만
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ? AND checked_in_by = ?", Integer.class,
            mapId, device.id())).isZero();
        assertThat(claimsOf(mapId)).containsExactly("KR-26010:" + accountId, "KR-26020:" + accountId, "KR-26030:" + accountId,
            "KR-26040:" + accountId);
        assertThat(jdbc.queryForObject("SELECT memo FROM visit WHERE map_id = ? AND region_code = 'KR-26040'", String.class, mapId))
            .isEqualTo("익명 선점");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ? AND region_code = 'KR-26010'", Integer.class,
            mapId)).isEqualTo(2); // 지도장 O 의 방문은 그대로(선점 아님)
        // 선점은 남에게 넘어가지 않았다: 선점 이전 없음, 지도장 XP 그대로
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%ClaimTransferred'",
            Long.class, mapId)).isEqualTo(claimTransfersBefore);
        assertThat(ledgerHas(owner.id(), "claim:" + mapId + ":KR-26010:" + owner.id())).isFalse();
        assertThat(xpOf(owner.id())).isEqualTo(ownerXp);
        // 계정 탐험가 진행(재계산 예약): 선점 보너스 refId 가 계정 기준으로
        assertThat(ledgerHas(accountId, "claim:" + mapId + ":KR-26010:" + accountId)).isTrue();
        assertThat(ledgerHas(accountId, "claim:" + mapId + ":KR-26040:" + accountId)).isTrue();
        assertThat(ledgerHas(accountId, "region:" + accountId + ":KR-26020#1")).isTrue();
        // 꾸미기(재계산): 옮겨 온 지역 아이템
        assertThat(jdbc.queryForList("SELECT item_id FROM owned_item WHERE explorer_id = ? AND item_id LIKE 'region:KR-260%' "
            + "ORDER BY item_id", String.class, accountId))
            .containsExactly("region:KR-26010", "region:KR-26020", "region:KR-26030", "region:KR-26040");
        // 도감(지도 단위): 칠해진 지역이 같으니 그대로
        assertThat(jdbc.queryForList("SELECT CONCAT(set_id, ':', collected_codes) FROM set_progress WHERE map_id = ? ORDER BY set_id",
            String.class, mapId)).isEqualTo(setProgressBefore);
        // 재전달 안전 + 다시 재계산해도 같다
        long accountXp = xpOf(accountId);
        merges.onMemberReassigned(new MemberReassigned(mapId, device.id(), accountId, clock.instant()));
        assertThat(recalculate.recalculateIfSettled(ExplorerId.of(accountId))).isTrue();
        assertThat(xpOf(accountId)).isEqualTo(accountXp);
        assertThat(claimsOf(mapId)).hasSize(4);
    }

    // ---- 3단계 잔여 P3-R3-1 ------------------------------------------------------------------------------------------

    @Test
    void 재계산_보류는_탐험_영토_구독자의_미전달_이벤트도_본다_P3_R3_1() throws Exception {
        Anonymous device = anonymous();
        checkIn(device, null, "KR-11010", LocalDate.now(clock)).andExpect(status().isCreated());
        awaitSettled(device.id());
        assertThat(recalculate.recalculateIfSettled(ExplorerId.of(device.id()))).isTrue();
        relay.pause();
        try {
            // exploration.territory 만 받는 이벤트(진행·꾸미기 구독자는 받지 않음)를 그 탐험가 지도에 남겨 둔다
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> outbox.append("ExpeditionMap",
                device.personalMapId(), new MemberPurged(device.personalMapId(), UUID.randomUUID().toString(), clock.instant())));
            assertThat(recalculate.recalculateIfSettled(ExplorerId.of(device.id()))).isFalse();
            assertThat(inventoryRecalculate.recalculateIfSettled(ExplorerId.of(device.id()))).isFalse();
        } finally {
            relay.resume();
        }
        awaitSettled(device.id());
        assertThat(recalculate.recalculateIfSettled(ExplorerId.of(device.id()))).isTrue();
        assertThat(inventoryRecalculate.recalculateIfSettled(ExplorerId.of(device.id()))).isTrue();
    }

    // ---- 인증 공존 · CSRF ------------------------------------------------------------------------------------------

    @Test
    void 세션이_토큰보다_먼저다_세션_없으면_토큰() throws Exception {
        Session session = devLogin(unique("both") + "@example.com", null, null);
        Anonymous other = anonymous();
        mvc.perform(get("/explorers/me").session(session.http()).header(H, other.token())).andExpect(status().isOk())
            .andExpect(jsonPath("$.explorerId").value(session.explorerId()));
        mvc.perform(get("/explorers/me").header(H, other.token())).andExpect(status().isOk())
            .andExpect(jsonPath("$.explorerId").value(other.id()));
        mvc.perform(get("/explorers/me")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("EXPLORER_TOKEN_REQUIRED"));
        // 세션은 있는데 계정이 사라짐(예: dev 초기화) → 토큰으로 넘어가지 않고 401
        jdbc.update("DELETE FROM account WHERE explorer_id = ?", session.explorerId());
        mvc.perform(get("/explorers/me").session(session.http()).header(H, other.token())).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void 세션_요청의_변경_메서드는_토큰_헤더가_있어도_CSRF_가_필요하다_세션_없는_요청은_제외() throws Exception {
        Session session = devLogin(unique("csrf") + "@example.com", null, null);
        String body = om.writeValueAsString(Map.of("regionCode", "KR-11020", "visitDate", LocalDate.now(clock).toString()));
        mvc.perform(post("/visits").session(session.http()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        // 쿠키 XSRF-TOKEN → 헤더 X-XSRF-TOKEN
        Cookie forged = new Cookie("XSRF-TOKEN", "attacker-chosen");
        mvc.perform(post("/visits").session(session.http()).cookie(xsrfCookie()).header("X-XSRF-TOKEN", forged.getValue())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        asSession(session, post("/visits").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        // 세션이 있으면 토큰 헤더가 있어도 CSRF 검사(헤더로 우회 불가 — QA P3-1)
        Anonymous someone = anonymous();
        mvc.perform(put("/me/handle").session(session.http()).header(H, someone.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"handle\":\"bypass\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(put("/me/handle").session(session.http()).header(H, "x").contentType(MediaType.APPLICATION_JSON)
            .content("{\"handle\":\"bypass\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/visits").session(session.http()).header("X-Admin-Token", "local-admin-token")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        // 세션 없는 익명 발급·토큰 헤더 요청은 CSRF 대상 아님(기존 클라이언트·테스트 그대로)
        Anonymous device = anonymous();
        checkIn(device, null, "KR-11020", LocalDate.now(clock)).andExpect(status().isCreated());
        // 로그아웃(CSRF 필요) → 세션 무효
        mvc.perform(post("/logout").session(session.http())).andExpect(status().isForbidden());
        asSession(session, post("/logout")).andExpect(status().isNoContent());
        assertThat(session.http().isInvalid()).isTrue();
    }

    // ---- handle ---------------------------------------------------------------------------------------------------

    @Test
    void handle_변경_형식_금칙어_중복_익명은_로그인_필요() throws Exception {
        Session mine = devLogin(unique("hdl") + "@example.com", null, null);
        Session other = devLogin(unique("hdx") + "@example.com", null, null);
        asSession(mine, put("/me/handle").contentType(MediaType.APPLICATION_JSON).content("{\"handle\":\"x!\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HANDLE_INVALID"));
        asSession(mine, put("/me/handle").contentType(MediaType.APPLICATION_JSON).content("{\"handle\":\"admin\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HANDLE_RESERVED"));
        asSession(mine, put("/me/handle").contentType(MediaType.APPLICATION_JSON)
            .content("{\"handle\":\"" + other.login().get("handle").asText() + "\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HANDLE_TAKEN"));
        String next = unique("New_");
        asSession(mine, put("/me/handle").contentType(MediaType.APPLICATION_JSON).content("{\"handle\":\"" + next + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.handle").value(next.toLowerCase()));
        assertThat(profiles.explorerIdByHandle(next)).contains(mine.explorerId());
        Anonymous device = anonymous();
        mvc.perform(put("/me/handle").header(H, device.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"handle\":\"someone\"}")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
        assertThat(profiles.handleOf(device.id())).isEmpty();
    }

    @Test
    void 바꾸기_전_handle_은_30일_예약된다_다른_사람은_못_쓰고_본인은_되돌린다_P3_10() throws Exception {
        Session mine = devLogin(unique("rsv") + "@example.com", null, null);
        Session other = devLogin(unique("rsx") + "@example.com", null, null);
        String original = mine.login().get("handle").asText();
        String next = unique("moved_");
        asSession(mine, put("/me/handle").contentType(MediaType.APPLICATION_JSON).content("{\"handle\":\"" + next + "\"}"))
            .andExpect(status().isOk());
        assertThat(profiles.explorerIdByHandle(original)).isEmpty(); // 공개 프로필은 새 handle 로
        asSession(other, put("/me/handle").contentType(MediaType.APPLICATION_JSON).content("{\"handle\":\"" + original + "\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HANDLE_TAKEN"));
        // 본인은 예약 기간 안에 되돌릴 수 있다
        asSession(mine, put("/me/handle").contentType(MediaType.APPLICATION_JSON).content("{\"handle\":\"" + original + "\"}"))
            .andExpect(status().isOk());
        // 이제 next 가 예약 — 기한(30일)이 지나면 풀린다
        asSession(other, put("/me/handle").contentType(MediaType.APPLICATION_JSON).content("{\"handle\":\"" + next + "\"}"))
            .andExpect(status().isConflict());
        clock.advance(Duration.ofDays(30).plusSeconds(1));
        asSession(other, put("/me/handle").contentType(MediaType.APPLICATION_JSON).content("{\"handle\":\"" + next + "\"}"))
            .andExpect(status().isOk());
    }

    // ---- 실제 OAuth 경로(MockMvc oidcLogin) ------------------------------------------------------------------------

    @Test
    void oidcLogin_인증은_계정으로_탐험가를_찾는다_계정이_없으면_401() throws Exception {
        ClientRegistration google = ClientRegistration.withRegistrationId("google").clientId("test-client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://accounts.example/auth").tokenUri("https://accounts.example/token").build();
        String sub = unique("1098");
        mvc.perform(get("/explorers/me").with(oidcLogin().clientRegistration(google).idToken(token -> token.subject(sub))))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        AccountService.LoginOutcome created = accounts.login(new AccountIdentity("google", sub, "oidc." + sub + "@gmail.com"), null);
        mvc.perform(get("/explorers/me").with(oidcLogin().clientRegistration(google).idToken(token -> token.subject(sub))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.explorerId").value(created.explorer().id().value()));
        mvc.perform(get("/auth/session").with(oidcLogin().clientRegistration(google).idToken(token -> token.subject(sub))))
            .andExpect(jsonPath("$.loggedIn").value(true))
            .andExpect(jsonPath("$.handle").value(created.explorer().handle().value()));
    }
}
