package com.kobi.territory.account;

import static com.kobi.territory.support.Explorers.방문;
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
import com.kobi.territory.exploration.api.event.ExplorerMerged;
import com.kobi.territory.exploration.api.event.MemberPurged;
import com.kobi.territory.exploration.api.event.MemberReassigned;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.exploration.application.AccountService;
import com.kobi.territory.exploration.application.ExplorerMergeService;
import com.kobi.territory.exploration.domain.explorer.AccountIdentity;
import com.kobi.territory.exploration.domain.explorer.LoginPlan;
import com.kobi.territory.outbox.OutboxRelay;
import com.kobi.territory.progression.application.RecalculateService;
import com.kobi.territory.support.Explorers;
import com.kobi.territory.support.Explorers.Anonymous;
import com.kobi.territory.support.Explorers.Session;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import com.kobi.territory.wardrobe.application.InventoryRecalculateService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 4단계 파트 A D2·D3: /dev/login(실제 OIDC 성공과 같은 경로) → 계정 연결/병합 → 이벤트 연쇄(ExplorerMerged → VisitsMerged →
 * 정리, MembershipHandover → MemberReassigned) → 재계산 예약으로 진행·인벤토리 재계산, 세션·토큰 인증 공존, CSRF, handle,
 * 구글 미설정 상태, oidcLogin() 포스트프로세서. 회귀 출처: 사용자 결정 Q1(랜덤 handle)·Q2(공유 지도 재귀속), QA P3-1(CSRF 우회),
 * P3-10(handle 예약), 3단계 잔여 P3-R3-1(재계산 보류에 탐험 구독자 포함).
 */
@IntegrationTest
@DisplayName("계정 로그인과 기록 합치기")
class AccountLoginIntegrationTest {

    private static final String H = Explorers.TOKEN;
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
    @Autowired Explorers explorers;

    private Anonymous 기기() throws Exception {
        return explorers.익명_탐험가();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return explorers.json(result);
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    private static String 새_이메일(String prefix) {
        return unique(prefix) + "@example.com";
    }

    private Session 로그인(String email, Anonymous device) throws Exception {
        return 로그인(email, null, device);
    }

    private Session 로그인(String email, String sub, Anonymous device) throws Exception {
        MockHttpServletRequestBuilder request = post("/dev/login").contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(sub == null ? Map.of("email", email) : Map.of("email", email, "sub", sub)));
        if (device != null) request.header(H, device.token());
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false),
            om.readTree(result.getResponse().getContentAsString()));
    }

    private ResultActions 칠한다(Anonymous who, String mapId, String code, LocalDate date) throws Exception {
        return explorers.체크인(who, 방문(code, date, null, mapId));
    }

    /**
     * 세션 + CSRF(쿠키 XSRF-TOKEN 값을 헤더 X-XSRF-TOKEN 으로 — 화면과 같은 방식). spring-security-test 의 csrf() 는 쓰지 않는다:
     * 공유 CsrfFilter 의 저장소를 세션 저장소로 바꿔 놓아 같은 컨텍스트의 다른 테스트가 쿠키를 못 받게 된다.
     */
    private ResultActions 세션으로(Session session, MockHttpServletRequestBuilder builder) throws Exception {
        Cookie xsrf = xsrfCookie();
        return mvc.perform(builder.session(session.http()).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()));
    }

    private Cookie xsrfCookie() throws Exception {
        Cookie xsrf = mvc.perform(get("/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).as("XSRF-TOKEN 쿠키").isNotNull();
        return xsrf;
    }

    private ResultActions 주소를_바꾼다(Session session, String handle) throws Exception {
        return 세션으로(session, put("/me/handle").contentType(MediaType.APPLICATION_JSON).content("{\"handle\":\"" + handle + "\"}"));
    }

    private List<String> visitsOf(String mapId) {
        return jdbc.queryForList("SELECT CONCAT(region_code, '@', visit_date, '@', checked_in_by) FROM visit WHERE map_id = ? "
            + "ORDER BY region_code", String.class, mapId);
    }

    private void 정리될_때까지(String explorerId) {
        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recalculation_request WHERE explorer_id = ?", Integer.class,
                explorerId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class)).isZero();
        });
    }

    private long xpOf(String explorerId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM xp_ledger WHERE explorer_id = ?", Long.class, explorerId);
    }

    private boolean 장부에_있다(String explorerId, String refId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id = ?", Integer.class,
            explorerId, refId) > 0;
    }

    private List<String> 선점(String mapId) {
        return jdbc.queryForList("SELECT CONCAT(region_code, ':', checked_in_by) FROM visit v WHERE map_id = ? AND hidden_at IS NULL "
            + "AND NOT EXISTS (SELECT 1 FROM visit o WHERE o.map_id = v.map_id AND o.region_code = v.region_code AND o.hidden_at IS NULL "
            + "AND (o.claim_rank_at < v.claim_rank_at OR (o.claim_rank_at = v.claim_rank_at AND o.visited_at < v.visited_at))) "
            + "ORDER BY region_code", String.class, mapId);
    }

    @Nested
    @DisplayName("구글 로그인이 설정되지 않았을 때")
    class GoogleNotConfigured {

        @Test
        @DisplayName("서비스는 그대로 뜨고 로그인만 꺼져 있다")
        void loginDisabled() throws Exception {
            mvc.perform(get("/auth/session")).andExpect(status().isOk())
                .andExpect(jsonPath("$.googleLoginEnabled").value(false))
                .andExpect(jsonPath("$.loginUrl").doesNotExist())
                .andExpect(jsonPath("$.loggedIn").value(false));
            mvc.perform(get("/oauth2/authorization/google")).andExpect(status().isNotFound());
            mvc.perform(post("/auth/login-intent")).andExpect(status().isOk())
                .andExpect(jsonPath("$.googleLoginEnabled").value(false));
        }
    }

    @Nested
    @DisplayName("처음 로그인할 때")
    class FirstLogin {

        record Linked(Anonymous device, String email, Session session) {}

        private Linked 종로를_칠한_기기로_로그인() throws Exception {
            Anonymous device = 기기();
            칠한다(device, null, "KR-11010", LocalDate.now(clock)).andExpect(status().isCreated());
            String email = unique("Choi.") + "+x@example.com";
            return new Linked(device, email, 로그인(email, device));
        }

        @Test
        @DisplayName("지금 기기의 익명 탐험가가 계정에 연결되고 칠한 영토가 그대로 보인다")
        void linksAnonymousExplorer() throws Exception {
            Linked linked = 종로를_칠한_기기로_로그인();
            Session session = linked.session();
            assertThat(session.outcome()).isEqualTo("LINKED");
            assertThat(session.explorerId()).isEqualTo(linked.device().id());
            assertThat(session.login().get("merge").isNull()).isTrue();
            mvc.perform(get("/territory").session(session.http())).andExpect(status().isOk())
                .andExpect(jsonPath("$.visits[0].regionCode").value("KR-11010"));
            mvc.perform(get("/explorers/me").session(session.http())).andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymous").value(false)).andExpect(jsonPath("$.handle").value(session.handle()));
        }

        @Test
        @DisplayName("공개 주소는 이메일과 무관한 무작위 이름으로 정해지고 그 이름으로 찾을 수 있다")
        void randomHandle() throws Exception {
            Linked linked = 종로를_칠한_기기로_로그인();
            String handle = linked.session().handle();
            assertThat(handle).matches("explorer-[a-z2-9]{4,6}").doesNotContain("choi");
            assertThat(profiles.explorerIdByHandle(" @" + handle.toUpperCase() + " ")).contains(linked.device().id());
            assertThat(profiles.handleOf(linked.device().id())).contains(handle);
            assertThat(profiles.accountLinked(linked.device().id())).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE event_type LIKE '%HandleChanged' AND aggregate_id = ?",
                Integer.class, linked.device().id())).isEqualTo(1);
        }

        @Test
        @DisplayName("연결되면 그 기기의 익명 토큰으로는 더 이상 들어갈 수 없다")
        void anonymousTokenRevoked() throws Exception {
            Linked linked = 종로를_칠한_기기로_로그인();
            mvc.perform(get("/territory").header(H, linked.device().token())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EXPLORER_TOKEN_INVALID"));
        }

        @Test
        @DisplayName("연결되었다는 안내는 한 번만 보인다")
        void linkNoticeShownOnce() throws Exception {
            Session session = 종로를_칠한_기기로_로그인().session();
            mvc.perform(get("/auth/session").session(session.http())).andExpect(jsonPath("$.loggedIn").value(true))
                .andExpect(jsonPath("$.handle").value(session.handle())).andExpect(jsonPath("$.mergeNotice.outcome").value("LINKED"));
            mvc.perform(get("/auth/session").session(session.http())).andExpect(jsonPath("$.mergeNotice").doesNotExist());
        }

        @Test
        @DisplayName("같은 계정으로 다시 로그인하면 같은 탐험가로 로그인만 한다")
        void secondLoginSignsIn() throws Exception {
            Linked linked = 종로를_칠한_기기로_로그인();
            Session again = 로그인(linked.email(), null);
            assertThat(again.outcome()).isEqualTo("SIGNED_IN");
            assertThat(again.explorerId()).isEqualTo(linked.device().id());
        }

        @Test
        @DisplayName("기기 없이 처음 로그인하면 새 탐험가가 생기고, 이메일 앞부분이 같아도 공개 주소는 서로 다르다")
        void createsNewExplorerWithDistinctHandles() throws Exception {
            String local = unique("dup");
            Session first = 로그인(local + "@a.example", null);
            Session second = 로그인(local + "@b.example", null);
            assertThat(first.outcome()).isEqualTo("CREATED");
            String firstHandle = first.handle();
            assertThat(firstHandle).startsWith("explorer-").doesNotContain(local);
            assertThat(second.handle()).startsWith("explorer-").isNotEqualTo(firstHandle);
            assertThat(profiles.handleOf(first.explorerId())).contains(firstHandle);
        }
    }

    @Nested
    @DisplayName("다른 기기의 익명 기록을 기존 계정으로 합칠 때")
    class Merge {

        /** 계정 B(종로 2026-09-01) + 기기 A(종로 2025-01-01 메모·가평·A 가 지도장인 공유 지도에 부산 중구, 친구 C 멤버). */
        record Merged(String email, Session account, Anonymous device, Anonymous friend, String sharedMapId, long xpBefore,
                      Session merged) {
            String accountId() { return account.explorerId(); }
            String accountMap() { return account.personalMapId(); }
        }

        private Merged 두_기기를_합친다() throws Exception {
            Anonymous first = 기기();
            칠한다(first, null, "KR-11010", LocalDate.of(2026, 9, 1)).andExpect(status().isCreated());
            String email = 새_이메일("merge");
            Session account = 로그인(email, first);
            정리될_때까지(account.explorerId());
            long xpBefore = json(mvc.perform(get("/progress").session(account.http()))).get("xp").asLong();

            Anonymous device = 기기();
            explorers.체크인(device, 방문("KR-11010", LocalDate.parse("2025-01-01"), "익명메모", null)).andExpect(status().isCreated());
            칠한다(device, null, "KR-31370", LocalDate.now(clock)).andExpect(status().isCreated());
            JsonNode map = explorers.공유_지도를_만든다(device, "병합 원정대");
            String sharedMapId = map.get("mapId").asText();
            Anonymous friend = 기기();
            explorers.합류한다(friend, map.get("inviteCode").asText());
            칠한다(device, sharedMapId, "KR-26010", LocalDate.now(clock)).andExpect(status().isCreated());
            정리될_때까지(device.id());

            Session merged = 로그인(email, device);
            return new Merged(email, account, device, friend, sharedMapId, xpBefore, merged);
        }

        @Test
        @DisplayName("합쳤다고 알리고, 익명 탐험가는 닫혀 그 기기 토큰으로는 더 들어갈 수 없다")
        void anonymousExplorerIsClosed() throws Exception {
            Merged m = 두_기기를_합친다();
            assertThat(m.merged().outcome()).isEqualTo("MERGED");
            assertThat(m.merged().explorerId()).isEqualTo(m.accountId());
            assertThat(m.merged().login().get("merge").get("fromExplorerId").asText()).isEqualTo(m.device().id());
            assertThat(m.merged().login().get("merge").get("movedRegions").asInt()).isEqualTo(2);
            assertThat(m.merged().login().get("merge").get("newRegions").asInt()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM explorer WHERE id = ?", String.class, m.device().id())).isEqualTo("MERGED");
            assertThat(jdbc.queryForObject("SELECT access_token_hash FROM explorer WHERE id = ?", String.class, m.device().id())).isNull();
            mvc.perform(get("/territory").header(H, m.device().token())).andExpect(status().isUnauthorized());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM explorer WHERE status = 'ACTIVE' AND id = ?", Integer.class,
                m.device().id())).as("다시 계산 전체 대상에서 빠진다").isZero();
        }

        @Test
        @DisplayName("개인 지도의 방문은 계정 영토로 옮겨지고, 같은 곳은 더 이른 방문일과 그 메모가 남는다")
        void personalVisitsMoveToAccount() throws Exception {
            Merged m = 두_기기를_합친다();
            정리될_때까지(m.accountId());
            assertThat(visitsOf(m.accountMap()))
                .containsExactly("KR-11010@2025-01-01@" + m.accountId(), "KR-31370@" + LocalDate.now(clock) + "@" + m.accountId());
            assertThat(jdbc.queryForObject("SELECT memo FROM visit WHERE map_id = ? AND region_code = 'KR-11010'", String.class,
                m.accountMap())).isEqualTo("익명메모");
            assertThat(visitsOf(m.device().personalMapId())).isEmpty();
        }

        @Test
        @DisplayName("익명 탐험가가 지도장이던 공유 지도는 계정이 그 자리와 방문을 잇고, 떠난 것으로 처리되지 않는다")
        void sharedMapSeatIsHandedOver() throws Exception {
            Merged m = 두_기기를_합친다();
            정리될_때까지(m.accountId());
            assertThat(jdbc.queryForObject("SELECT owner_id FROM expedition_map WHERE id = ?", String.class, m.sharedMapId()))
                .isEqualTo(m.accountId());
            assertThat(jdbc.queryForList("SELECT CONCAT(explorer_id, ':', role, ':', CASE WHEN left_at IS NULL THEN 'TRUE' ELSE 'FALSE' END) "
                + "FROM map_member WHERE map_id = ?", String.class, m.sharedMapId()))
                .containsExactlyInAnyOrder(m.accountId() + ":OWNER:TRUE", m.friend().id() + ":MEMBER:TRUE");
            assertThat(visitsOf(m.sharedMapId())).containsExactly("KR-26010@" + LocalDate.now(clock) + "@" + m.accountId());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ? AND hidden_at IS NOT NULL", Integer.class,
                m.sharedMapId())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%MemberLeft'",
                Integer.class, m.sharedMapId())).isZero();
            mvc.perform(get("/maps").session(m.merged().http())).andExpect(status().isOk())
                .andExpect(jsonPath("$[1].mapId").value(m.sharedMapId()));
        }

        @Test
        @DisplayName("진행과 가방은 다시 계산되어 옮겨 온 곳의 경험치와 아이템이 생기고, 또 계산해도 같다")
        void progressAndBagAreRecalculated() throws Exception {
            Merged m = 두_기기를_합친다();
            정리될_때까지(m.accountId());
            JsonNode progress = json(mvc.perform(get("/progress").session(m.merged().http())));
            assertThat(progress.get("xp").asLong()).isGreaterThan(m.xpBefore());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE ref_id = ?", Integer.class,
                "region:" + m.accountId() + ":KR-31370#1")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM owned_item WHERE explorer_id = ? AND item_id = 'region:KR-31370'",
                Integer.class, m.accountId())).isEqualTo(1);
            assertThat(recalculate.recalculateIfSettled(ExplorerId.of(m.accountId()))).isTrue();
            assertThat(json(mvc.perform(get("/progress").session(m.merged().http()))).get("xp").asLong())
                .isEqualTo(progress.get("xp").asLong());
        }

        @Test
        @DisplayName("같은 합치기 소식이 다시 와도, 같은 기기로 다시 로그인해도 결과는 그대로다")
        void mergeIsIdempotent() throws Exception {
            Merged m = 두_기기를_합친다();
            정리될_때까지(m.accountId());
            merges.onExplorerMerged(new ExplorerMerged(m.device().id(), m.accountId(), m.device().personalMapId(), m.accountMap(),
                clock.instant()));
            assertThat(visitsOf(m.accountMap())).hasSize(2);
            AccountService.LoginOutcome replay = accounts.login(new AccountIdentity("google", "dev:" + m.email(), m.email()),
                ExplorerId.of(m.device().id()));
            assertThat(replay.kind()).isEqualTo(LoginPlan.Kind.SIGN_IN);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE event_type LIKE '%ExplorerMerged' AND aggregate_id = ?",
                Integer.class, m.accountId())).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("계정과 익명 탐험가가 같은 공유 지도의 멤버일 때 합치면")
    class Reassignment {

        /**
         * KR-26010: 익명 A 선점 → 지도장 O. KR-26020: A 혼자. KR-26030: 계정 B 선점 → A. KR-26040: A 선점(메모) → B(방문일은 B 가 더 이름).
         */
        record Shared(Anonymous owner, String mapId, String accountId, Anonymous device, long ownerXp, List<String> setProgressBefore,
                      long claimTransfersBefore) {}

        private Shared 선점이_얽힌_지도를_합친다() throws Exception {
            Anonymous owner = 기기();
            JsonNode map = explorers.공유_지도를_만든다(owner, "재귀속 원정대");
            String mapId = map.get("mapId").asText();
            String invite = "{\"inviteCode\":\"" + map.get("inviteCode").asText() + "\"}";
            String email = 새_이메일("reassign");
            Session account = 로그인(email, 기기());
            String accountId = account.explorerId();
            세션으로(account, post("/maps/join").contentType(MediaType.APPLICATION_JSON).content(invite)).andExpect(status().isOk());
            Anonymous device = 기기();
            explorers.합류한다(device, map.get("inviteCode").asText());

            칠한다(device, mapId, "KR-26010", LocalDate.now(clock)).andExpect(status().isCreated());
            칠한다(owner, mapId, "KR-26010", LocalDate.now(clock)).andExpect(status().isCreated());
            칠한다(device, mapId, "KR-26020", LocalDate.now(clock)).andExpect(status().isCreated());
            clock.advance(Duration.ofSeconds(1));
            세션으로(account, post("/visits").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(
                Map.of("regionCode", "KR-26030", "visitDate", LocalDate.now(clock).toString(), "mapId", mapId)))).andExpect(status().isCreated());
            칠한다(device, mapId, "KR-26030", LocalDate.now(clock)).andExpect(status().isCreated());
            explorers.체크인(device, 방문("KR-26040", LocalDate.now(clock), "익명 선점", mapId)).andExpect(status().isCreated());
            clock.advance(Duration.ofSeconds(1));
            세션으로(account, post("/visits").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(
                Map.of("regionCode", "KR-26040", "visitDate", "2020-01-01", "mapId", mapId)))).andExpect(status().isCreated());
            정리될_때까지(device.id());
            정리될_때까지(accountId);
            long ownerXp = xpOf(owner.id());
            List<String> setProgressBefore = jdbc.queryForList(
                "SELECT CONCAT(set_id, ':', collected_codes) FROM set_progress WHERE map_id = ? ORDER BY set_id", String.class, mapId);
            long claimTransfersBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%ClaimTransferred'", Long.class, mapId);

            Session merged = 로그인(email, device);
            assertThat(merged.outcome()).isEqualTo("MERGED");
            정리될_때까지(accountId);
            return new Shared(owner, mapId, accountId, device, ownerXp, setProgressBefore, claimTransfersBefore);
        }

        @Test
        @DisplayName("익명 쪽 방문과 선점이 선점 순서 그대로 계정 것이 되고, 겹친 곳은 먼저 선점한 쪽의 기록이 남는다")
        void visitsAndClaimsMoveInClaimOrder() throws Exception {
            Shared s = 선점이_얽힌_지도를_합친다();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ? AND checked_in_by = ?", Integer.class,
                s.mapId(), s.device().id())).isZero();
            assertThat(선점(s.mapId())).containsExactly("KR-26010:" + s.accountId(), "KR-26020:" + s.accountId(),
                "KR-26030:" + s.accountId(), "KR-26040:" + s.accountId());
            assertThat(jdbc.queryForObject("SELECT memo FROM visit WHERE map_id = ? AND region_code = 'KR-26040'", String.class, s.mapId()))
                .isEqualTo("익명 선점");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ? AND region_code = 'KR-26010'", Integer.class,
                s.mapId())).as("지도장의 방문은 그대로").isEqualTo(2);
        }

        @Test
        @DisplayName("다른 멤버에게 선점이 넘어가지 않아 지도장의 경험치도 그대로다")
        void othersKeepTheirClaims() throws Exception {
            Shared s = 선점이_얽힌_지도를_합친다();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%ClaimTransferred'",
                Long.class, s.mapId())).isEqualTo(s.claimTransfersBefore());
            assertThat(장부에_있다(s.owner().id(), "claim:" + s.mapId() + ":KR-26010:" + s.owner().id())).isFalse();
            assertThat(xpOf(s.owner().id())).isEqualTo(s.ownerXp());
        }

        @Test
        @DisplayName("계정 탐험가는 다시 계산되어 선점 보너스·기본 경험치·지역 아이템을 받고, 지도 도감은 바뀌지 않는다")
        void accountIsRecalculated() throws Exception {
            Shared s = 선점이_얽힌_지도를_합친다();
            assertThat(장부에_있다(s.accountId(), "claim:" + s.mapId() + ":KR-26010:" + s.accountId())).isTrue();
            assertThat(장부에_있다(s.accountId(), "claim:" + s.mapId() + ":KR-26040:" + s.accountId())).isTrue();
            assertThat(장부에_있다(s.accountId(), "region:" + s.accountId() + ":KR-26020#1")).isTrue();
            assertThat(jdbc.queryForList("SELECT item_id FROM owned_item WHERE explorer_id = ? AND item_id LIKE 'region:KR-260%' "
                + "ORDER BY item_id", String.class, s.accountId()))
                .containsExactly("region:KR-26010", "region:KR-26020", "region:KR-26030", "region:KR-26040");
            assertThat(jdbc.queryForList("SELECT CONCAT(set_id, ':', collected_codes) FROM set_progress WHERE map_id = ? ORDER BY set_id",
                String.class, s.mapId())).isEqualTo(s.setProgressBefore());
        }

        @Test
        @DisplayName("같은 넘기기 소식이 다시 오고 또 계산해도 결과는 그대로다")
        void reassignmentIsIdempotent() throws Exception {
            Shared s = 선점이_얽힌_지도를_합친다();
            long accountXp = xpOf(s.accountId());
            merges.onMemberReassigned(new MemberReassigned(s.mapId(), s.device().id(), s.accountId(), clock.instant()));
            assertThat(recalculate.recalculateIfSettled(ExplorerId.of(s.accountId()))).isTrue();
            assertThat(xpOf(s.accountId())).isEqualTo(accountXp);
            assertThat(선점(s.mapId())).hasSize(4);
        }
    }

    @Nested
    @DisplayName("다시 계산")
    class Recalculation {

        @Test
        @DisplayName("탐험 쪽에만 가는 소식이 아직 전달되지 않았어도 진행과 가방의 다시 계산을 미룬다")
        void deferredForExplorationBacklog() throws Exception {
            Anonymous device = 기기();
            칠한다(device, null, "KR-11010", LocalDate.now(clock)).andExpect(status().isCreated());
            정리될_때까지(device.id());
            assertThat(recalculate.recalculateIfSettled(ExplorerId.of(device.id()))).isTrue();
            relay.pause();
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> outbox.append("ExpeditionMap",
                    device.personalMapId(), new MemberPurged(device.personalMapId(), UUID.randomUUID().toString(), clock.instant())));
                assertThat(recalculate.recalculateIfSettled(ExplorerId.of(device.id()))).isFalse();
                assertThat(inventoryRecalculate.recalculateIfSettled(ExplorerId.of(device.id()))).isFalse();
            } finally {
                relay.resume();
            }
            정리될_때까지(device.id());
            assertThat(recalculate.recalculateIfSettled(ExplorerId.of(device.id()))).isTrue();
            assertThat(inventoryRecalculate.recalculateIfSettled(ExplorerId.of(device.id()))).isTrue();
        }
    }

    @Nested
    @DisplayName("로그인 세션과 기기 토큰이 함께 올 때")
    class SessionAndToken {

        @Test
        @DisplayName("로그인 세션이 기기 토큰보다 먼저다")
        void sessionWins() throws Exception {
            Session session = 로그인(새_이메일("both"), null);
            mvc.perform(get("/explorers/me").session(session.http()).header(H, 기기().token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.explorerId").value(session.explorerId()));
        }

        @Test
        @DisplayName("세션이 없으면 기기 토큰의 탐험가다")
        void tokenWithoutSession() throws Exception {
            Anonymous other = 기기();
            mvc.perform(get("/explorers/me").header(H, other.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.explorerId").value(other.id()));
        }

        @Test
        @DisplayName("둘 다 없으면 누구인지 밝히라며 거절된다")
        void neither() throws Exception {
            mvc.perform(get("/explorers/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EXPLORER_TOKEN_REQUIRED"));
        }

        @Test
        @DisplayName("세션의 계정이 사라졌으면 기기 토큰으로 넘어가지 않고 계정을 찾을 수 없다며 거절된다")
        void vanishedAccountDoesNotFallBack() throws Exception {
            Session session = 로그인(새_이메일("both"), null);
            jdbc.update("DELETE FROM account WHERE explorer_id = ?", session.explorerId());
            mvc.perform(get("/explorers/me").session(session.http()).header(H, 기기().token())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("로그인 세션으로 무언가를 바꿀 때")
    class Csrf {

        private String 종로_중구_체크인() throws Exception {
            return om.writeValueAsString(Map.of("regionCode", "KR-11020", "visitDate", LocalDate.now(clock).toString()));
        }

        @Test
        @DisplayName("위조 방지 토큰이 없으면 거절된다")
        void missingTokenRejected() throws Exception {
            Session session = 로그인(새_이메일("csrf"), null);
            mvc.perform(post("/visits").session(session.http()).contentType(MediaType.APPLICATION_JSON).content(종로_중구_체크인()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        }

        @Test
        @DisplayName("쿠키와 다른 위조 방지 토큰은 거절된다")
        void forgedTokenRejected() throws Exception {
            Session session = 로그인(새_이메일("csrf"), null);
            mvc.perform(post("/visits").session(session.http()).cookie(xsrfCookie()).header("X-XSRF-TOKEN", "attacker-chosen")
                .contentType(MediaType.APPLICATION_JSON).content(종로_중구_체크인())).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("화면처럼 쿠키의 위조 방지 토큰을 함께 보내면 칠해진다")
        void validTokenAccepted() throws Exception {
            Session session = 로그인(새_이메일("csrf"), null);
            세션으로(session, post("/visits").contentType(MediaType.APPLICATION_JSON).content(종로_중구_체크인()))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("기기 토큰이나 관리자 토큰을 함께 보내도 위조 방지 검사를 건너뛸 수 없다")
        void otherHeadersDoNotBypass() throws Exception {
            Session session = 로그인(새_이메일("csrf"), null);
            mvc.perform(put("/me/handle").session(session.http()).header(H, 기기().token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"handle\":\"bypass\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
            mvc.perform(put("/me/handle").session(session.http()).header(H, "x").contentType(MediaType.APPLICATION_JSON)
                .content("{\"handle\":\"bypass\"}")).andExpect(status().isForbidden());
            mvc.perform(post("/visits").session(session.http()).header("X-Admin-Token", "local-admin-token")
                .contentType(MediaType.APPLICATION_JSON).content(종로_중구_체크인())).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("세션 없는 기기의 요청은 위조 방지 검사 없이 칠해진다")
        void tokenOnlyRequestsAreExempt() throws Exception {
            칠한다(기기(), null, "KR-11020", LocalDate.now(clock)).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("로그아웃에도 위조 방지 토큰이 필요하고, 로그아웃하면 세션이 끝난다")
        void logout() throws Exception {
            Session session = 로그인(새_이메일("csrf"), null);
            mvc.perform(post("/logout").session(session.http())).andExpect(status().isForbidden());
            세션으로(session, post("/logout")).andExpect(status().isNoContent());
            assertThat(session.http().isInvalid()).isTrue();
        }
    }

    @Nested
    @DisplayName("공개 주소를 바꿀 때")
    class Handle {

        @Test
        @DisplayName("바꾸면 소문자로 정리되어 새 주소로 찾을 수 있다")
        void changeHandle() throws Exception {
            Session mine = 로그인(새_이메일("hdl"), null);
            String next = unique("New_");
            주소를_바꾼다(mine, next).andExpect(status().isOk()).andExpect(jsonPath("$.handle").value(next.toLowerCase()));
            assertThat(profiles.explorerIdByHandle(next)).contains(mine.explorerId());
        }

        @Test
        @DisplayName("형식에 맞지 않는 주소는 쓸 수 없다")
        void invalidFormat() throws Exception {
            주소를_바꾼다(로그인(새_이메일("hdl"), null), "x!").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HANDLE_INVALID"));
        }

        @Test
        @DisplayName("서비스가 쓰는 예약어는 쓸 수 없다")
        void reservedWord() throws Exception {
            주소를_바꾼다(로그인(새_이메일("hdl"), null), "admin").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HANDLE_RESERVED"));
        }

        @Test
        @DisplayName("다른 사람이 쓰는 주소는 쓸 수 없다")
        void takenByOther() throws Exception {
            Session other = 로그인(새_이메일("hdx"), null);
            주소를_바꾼다(로그인(새_이메일("hdl"), null), other.handle()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDLE_TAKEN"));
        }

        @Test
        @DisplayName("익명 탐험가는 로그인해야 주소를 가질 수 있다")
        void anonymousNeedsLogin() throws Exception {
            Anonymous device = 기기();
            mvc.perform(put("/me/handle").header(H, device.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"handle\":\"someone\"}")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
            assertThat(profiles.handleOf(device.id())).isEmpty();
        }

        @Test
        @DisplayName("바꾸기 전 주소는 다른 사람이 쓸 수 없고 그 주소로는 더 이상 찾을 수 없다")
        void previousHandleIsReserved() throws Exception {
            Session mine = 로그인(새_이메일("rsv"), null);
            Session other = 로그인(새_이메일("rsx"), null);
            String original = mine.handle();
            주소를_바꾼다(mine, unique("moved_")).andExpect(status().isOk());
            assertThat(profiles.explorerIdByHandle(original)).isEmpty();
            주소를_바꾼다(other, original).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HANDLE_TAKEN"));
        }

        @Test
        @DisplayName("본인은 예약 기간 안에 예전 주소로 되돌릴 수 있다")
        void ownerCanRevert() throws Exception {
            Session mine = 로그인(새_이메일("rsv"), null);
            String original = mine.handle();
            주소를_바꾼다(mine, unique("moved_")).andExpect(status().isOk());
            주소를_바꾼다(mine, original).andExpect(status().isOk());
        }

        @Test
        @DisplayName("예약은 30일이 지나면 풀려 다른 사람이 쓸 수 있다")
        void reservationExpiresAfter30Days() throws Exception {
            Session mine = 로그인(새_이메일("rsv"), null);
            Session other = 로그인(새_이메일("rsx"), null);
            String original = mine.handle();
            String next = unique("moved_");
            주소를_바꾼다(mine, next).andExpect(status().isOk());
            주소를_바꾼다(mine, original).andExpect(status().isOk());
            주소를_바꾼다(other, next).andExpect(status().isConflict());
            clock.advance(Duration.ofDays(30).plusSeconds(1));
            주소를_바꾼다(other, next).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("실제 구글 로그인으로 들어올 때")
    class OidcLogin {

        private final ClientRegistration google = ClientRegistration.withRegistrationId("google").clientId("test-client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://accounts.example/auth").tokenUri("https://accounts.example/token").build();

        @Test
        @DisplayName("연결된 계정이 없으면 계정을 찾을 수 없다며 거절된다")
        void noAccount() throws Exception {
            String sub = unique("1098");
            mvc.perform(get("/explorers/me").with(oidcLogin().clientRegistration(google).idToken(token -> token.subject(sub))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        }

        @Test
        @DisplayName("계정이 있으면 그 탐험가로 들어가고 로그인 상태와 공개 주소가 보인다")
        void accountFindsExplorer() throws Exception {
            String sub = unique("1098");
            AccountService.LoginOutcome created = accounts.login(new AccountIdentity("google", sub, "oidc." + sub + "@gmail.com"), null);
            mvc.perform(get("/explorers/me").with(oidcLogin().clientRegistration(google).idToken(token -> token.subject(sub))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.explorerId").value(created.explorer().id().value()));
            mvc.perform(get("/auth/session").with(oidcLogin().clientRegistration(google).idToken(token -> token.subject(sub))))
                .andExpect(jsonPath("$.loggedIn").value(true))
                .andExpect(jsonPath("$.handle").value(created.explorer().handle().value()));
        }
    }
}
