package com.kobi.territory.sharing;

import static com.kobi.territory.support.Explorers.새_이메일;
import static com.kobi.territory.support.Explorers.방문;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
import com.kobi.territory.support.Explorers;
import com.kobi.territory.support.Explorers.Anonymous;
import com.kobi.territory.support.Explorers.Session;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 4단계 파트 B D2·D3 + 06 연간 리캡 JSON: 카드 lazy 렌더(첫 요청 렌더 → 캐시 → 공개 요약이 바뀌면 낡음 → 최소 TTL 뒤 다시 렌더), 공개 프로필
 * HTML·OG meta, 공개 범위(기본 PRIVATE → 404 존재 숨김), 프로필 링크 합류 + 초대 보상, VS 비저장, MemberJoined 하위 호환, 리캡.
 * 회귀 출처: QA P2-1(병합 직후 렌더·칭호만 변경), P2-2(로그인 직후 익명 카드), P3-2(og:image 는 설정 주소), P3-5(프로필 비공개면 합류 404),
 * P3-6(병합 시 초대 보상 이전), 사용자 결정 Q1(기본 PRIVATE), 06 QA P2-1(리캡 JSON).
 */
@IntegrationTest
@DisplayName("공개 프로필과 공유 카드")
class SharingIntegrationTest {

    private static final String H = Explorers.TOKEN;
    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final String MEMO = "비밀메모-" + UUID.randomUUID().toString().substring(0, 4);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired ShowcaseReader showcases;
    @Autowired Explorers explorers;

    private Anonymous 기기() throws Exception {
        return explorers.익명_탐험가();
    }

    private Session 로그인(Anonymous device) throws Exception {
        return explorers.로그인(device, 새_이메일("share"));
    }

    private Session 로그인(Anonymous device, String email) throws Exception {
        return explorers.로그인(device, email);
    }

    private void 공개한다(Session session) throws Exception {
        세션으로(session, put("/me/privacy").contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PUBLIC\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.publiclyVisible").value(true));
    }

    private ResultActions 공개_범위(Session session, String visibility) throws Exception {
        return 세션으로(session, put("/me/privacy").contentType(MediaType.APPLICATION_JSON)
            .content("{\"visibility\":\"" + visibility + "\"}"));
    }

    private void 정리될_때까지() {
        explorers.전달이_끝날_때까지();
    }

    private ResultActions 세션으로(Session session, MockHttpServletRequestBuilder builder) throws Exception {
        return explorers.세션으로(session, builder);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return explorers.json(result);
    }

    private void 칠한다(Anonymous who, String code, LocalDate date, String memo) throws Exception {
        explorers.체크인(who, 방문(code, date, memo, null)).andExpect(status().isCreated());
    }

    private void 칠한다(Session who, String code, LocalDate date, String memo) throws Exception {
        clock.advance(Duration.ofSeconds(1));
        세션으로(who, post("/visits").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(방문(code, date, memo, null))))
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

    private List<String> 가방(String explorerId, String prefix) {
        return jdbc.queryForList("SELECT item_id FROM owned_item WHERE explorer_id = ? AND item_id LIKE ? ORDER BY item_id",
            String.class, explorerId, prefix + "%");
    }

    private static BufferedImage png(MvcResult result) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
    }

    private String html(String path) throws Exception {
        return mvc.perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("내 영토 카드 미리보기")
    class MyCards {

        record Drawn(Anonymous me, Instant firstRender) {}

        private Drawn 한_번_그린_카드() throws Exception {
            Anonymous me = 기기();
            칠한다(me, "KR-11010", LocalDate.now(clock), MEMO);
            정리될_때까지();
            mvc.perform(get("/me/cards").header(H, me.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").doesNotExist())
                .andExpect(jsonPath("$.cards[0].kind").value("TERRITORY"))
                .andExpect(jsonPath("$.cards[0].rendered").value(false));
            MvcResult first = mvc.perform(get("/me/cards/territory.png").header(H, me.token())).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG)).andReturn();
            assertThat(png(first).getWidth()).isEqualTo(1200);
            assertThat(renderedAt(me.id(), "TERRITORY")).isNotNull();
            return new Drawn(me, renderedAt(me.id(), "TERRITORY"));
        }

        @Test
        @DisplayName("처음 볼 때 그려지고, 공개 요약이 그대로면 다시 그리지 않는다")
        void drawnOnFirstViewThenReused() throws Exception {
            Drawn drawn = 한_번_그린_카드();
            clock.advance(Duration.ofMinutes(1));
            mvc.perform(get("/me/cards/territory.png").header(H, drawn.me().token())).andExpect(status().isOk());
            assertThat(renderedAt(drawn.me().id(), "TERRITORY")).as("원천 그대로 — 캐시").isEqualTo(drawn.firstRender());
        }

        @Test
        @DisplayName("새로 칠하면 낡은 카드로 표시되지만 그린 지 10분 안에는 그린 카드를 그대로 준다")
        void staleButWithinMinimumAge() throws Exception {
            Drawn drawn = 한_번_그린_카드();
            칠한다(drawn.me(), "KR-11020", LocalDate.now(clock), null);
            정리될_때까지();
            mvc.perform(get("/me/cards").header(H, drawn.me().token())).andExpect(jsonPath("$.cards[0].stale").value(true));
            mvc.perform(get("/me/cards/territory.png").header(H, drawn.me().token())).andExpect(status().isOk());
            assertThat(renderedAt(drawn.me().id(), "TERRITORY")).isEqualTo(drawn.firstRender());
        }

        @Test
        @DisplayName("낡은 카드는 10분이 지나면 다시 그려진다")
        void redrawnAfterMinimumAge() throws Exception {
            Drawn drawn = 한_번_그린_카드();
            칠한다(drawn.me(), "KR-11020", LocalDate.now(clock), null);
            정리될_때까지();
            clock.advance(Duration.ofMinutes(10));
            mvc.perform(get("/me/cards/territory.png").header(H, drawn.me().token())).andExpect(status().isOk());
            assertThat(renderedAt(drawn.me().id(), "TERRITORY")).isAfter(drawn.firstRender());
            mvc.perform(get("/me/cards").header(H, drawn.me().token())).andExpect(jsonPath("$.cards[0].stale").value(false))
                .andExpect(jsonPath("$.cards[0].rendered").value(true));
        }

        @Test
        @DisplayName("없는 카드 종류는 없다고 알린다")
        void unknownKind() throws Exception {
            mvc.perform(get("/me/cards/vs.png").header(H, 기기().token())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CARD_KIND_NOT_FOUND"));
        }

        @Test
        @DisplayName("누구인지 모르면 볼 수 없다")
        void needsExplorer() throws Exception {
            mvc.perform(get("/me/cards")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("공개 프로필")
    class PublicProfile {

        /** 9월 17일 종로(메모)를 칠한 기기로 로그인하고 10월 2일 대구 중구(메모)를 칠한 주인. */
        private Session 두_달에_칠한_주인() throws Exception {
            Anonymous device = 기기();
            칠한다(device, "KR-11010", LocalDate.of(2026, 9, 17), MEMO);
            Session owner = 로그인(device);
            칠한다(owner, "KR-26010", LocalDate.of(2026, 10, 2), MEMO + "2");
            return owner;
        }

        private Session 공개한_주인() throws Exception {
            Session owner = 두_달에_칠한_주인();
            공개한다(owner);
            await().atMost(WAIT).untilAsserted(() -> mvc.perform(get("/u/" + owner.handle()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("data-region=\"KR-26010\""))));
            return owner;
        }

        private String 프로필_화면(Session owner) throws Exception {
            return mvc.perform(get("/u/" + owner.handle()).header("Host", "evil.example")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("처음에는 비공개라 공개하기 전에는 프로필이 없는 것처럼 보인다")
        void privateByDefault() throws Exception {
            Session owner = 두_달에_칠한_주인();
            mvc.perform(get("/u/" + owner.handle())).andExpect(status().isNotFound());
            세션으로(owner, get("/me/privacy")).andExpect(jsonPath("$.visibility").value("PRIVATE"));
        }

        @Test
        @DisplayName("공개하면 로그인하지 않은 사람도 열어 칠한 곳을 본다")
        void publicProfileOpensForAnyone() throws Exception {
            Session owner = 공개한_주인();
            assertThat(프로필_화면(owner)).contains("data-region=\"KR-26010\"").contains("og:title").contains("og:description");
        }

        @Test
        @DisplayName("메모·사진·정확한 날짜는 없고 방문은 달 단위로만 보인다")
        void onlyMonthsNoMemoOrPhoto() throws Exception {
            Session owner = 공개한_주인();
            assertThat(프로필_화면(owner)).contains("2026년 9월").contains("2026년 10월").contains("data-month=\"2026-10\"")
                .doesNotContain(MEMO).doesNotContain("2026-09-17").doesNotContain("2026-10-02").doesNotContain("photo");
        }

        @Test
        @DisplayName("공유 미리보기 그림 주소는 설정한 서비스 주소를 쓰고 요청에 적힌 주소를 따르지 않는다")
        void previewImageUsesConfiguredBaseUrl() throws Exception {
            Session owner = 공개한_주인();
            assertThat(프로필_화면(owner))
                .contains("<meta property=\"og:image\" content=\"http://localhost:8080/u/" + owner.handle() + "/card/territory.png\">")
                .doesNotContain("evil.example");
        }

        @Test
        @DisplayName("영토·최근 여행·리캡 카드가 공개 그림으로 열리고 주소의 대소문자를 가리지 않는다")
        void cardsArePublic() throws Exception {
            Session owner = 공개한_주인();
            mvc.perform(get("/u/" + owner.handle() + "/card/territory.png")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG)).andExpect(header().exists("Cache-Control"));
            mvc.perform(get("/u/" + owner.handle() + "/card/recent.png")).andExpect(status().isOk());
            mvc.perform(get("/u/" + owner.handle() + "/card/recap.png")).andExpect(status().isOk());
            mvc.perform(get("/u/" + owner.handle().toUpperCase() + "/card/territory.png")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("없는 카드나 없는 사람은 없다고 알린다")
        void unknownCardOrPerson() throws Exception {
            Session owner = 공개한_주인();
            mvc.perform(get("/u/" + owner.handle() + "/card/selfie.png")).andExpect(status().isNotFound());
            mvc.perform(get("/u/nobody-" + UUID.randomUUID().toString().substring(0, 5))).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("내 카드 목록에 handle과 공개 프로필·카드 링크가 보인다")
        void myCardsShowPublicUrls() throws Exception {
            Session owner = 공개한_주인();
            세션으로(owner, get("/me/cards")).andExpect(jsonPath("$.handle").value(owner.handle()))
                .andExpect(jsonPath("$.profileUrl").value("/u/" + owner.handle()))
                .andExpect(jsonPath("$.cards[1].publicUrl").value("/u/" + owner.handle() + "/card/recent.png"));
        }

        @Test
        @DisplayName("다시 비공개로 돌리면 프로필과 카드가 사라지지만 내 미리보기는 그대로 볼 수 있다")
        void backToPrivate() throws Exception {
            Session owner = 공개한_주인();
            공개_범위(owner, "PRIVATE").andExpect(status().isOk()).andExpect(jsonPath("$.publiclyVisible").value(false));
            mvc.perform(get("/u/" + owner.handle())).andExpect(status().isNotFound())
                .andExpect(content().string(Matchers.not(Matchers.containsString(owner.handle()))));
            mvc.perform(get("/u/" + owner.handle() + "/card/territory.png")).andExpect(status().isNotFound());
            세션으로(owner, get("/me/cards/territory.png")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("친구 공개로 바꾸면 친구가 아닌 사람에게는 없는 것처럼 보이고, 다시 공개하면 열린다")
        void friendsOnlyHidesFromStrangers() throws Exception {
            Session owner = 공개한_주인();
            공개_범위(owner, "FRIENDS").andExpect(jsonPath("$.visibility").value("FRIENDS"))
                .andExpect(jsonPath("$.publiclyVisible").value(false));
            mvc.perform(get("/u/" + owner.handle())).andExpect(status().isNotFound());
            공개_범위(owner, "PUBLIC").andExpect(status().isOk());
            mvc.perform(get("/u/" + owner.handle())).andExpect(status().isOk());
        }

        @Test
        @DisplayName("알 수 없는 공개 범위로는 바꿀 수 없다")
        void unknownVisibility() throws Exception {
            Session owner = 두_달에_칠한_주인();
            공개_범위(owner, "everyone").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_VISIBILITY"));
        }
    }

    @Nested
    @DisplayName("영토 비교 카드")
    class VersusCard {

        record Pair(Session mine, Session theirs) {}

        private Pair 둘_다_공개한_탐험가() throws Exception {
            Session mine = 로그인(기기());
            Session theirs = 로그인(기기());
            공개한다(mine);
            공개한다(theirs);
            칠한다(mine, "KR-11010", LocalDate.now(clock), "m");
            칠한다(theirs, "KR-11010", LocalDate.now(clock), "t");
            return new Pair(mine, theirs);
        }

        @Test
        @DisplayName("둘 다 공개면 비교 카드를 보여 주지만 내 카드로 남기지는 않는다")
        void drawnWithoutStoring() throws Exception {
            Pair pair = 둘_다_공개한_탐험가();
            MvcResult result = mvc.perform(get("/u/" + pair.mine().handle() + "/vs/" + pair.theirs().handle() + ".png"))
                .andExpect(status().isOk()).andReturn();
            assertThat(png(result).getHeight()).isEqualTo(630);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM share_card WHERE kind = 'VS'", Integer.class)).isZero();
        }

        @Test
        @DisplayName("자기 자신과는 비교할 수 없다")
        void notWithSelf() throws Exception {
            Pair pair = 둘_다_공개한_탐험가();
            mvc.perform(get("/u/" + pair.mine().handle() + "/vs/" + pair.mine().handle() + ".png")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("한쪽이 비공개면 없는 것처럼 보인다")
        void hiddenIfEitherPrivate() throws Exception {
            Pair pair = 둘_다_공개한_탐험가();
            공개_범위(pair.theirs(), "PRIVATE").andExpect(status().isOk());
            mvc.perform(get("/u/" + pair.mine().handle() + "/vs/" + pair.theirs().handle() + ".png")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("프로필 링크로 공유 지도에 합류할 때")
    class JoinViaProfile {

        record Host(Session host, String mapId, String joinBody) {}

        private Host 공개_프로필의_지도장() throws Exception {
            Session host = 로그인(기기());
            공개한다(host);
            String mapId = json(세션으로(host, post("/maps").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"부산 원정대\"}"))
                .andExpect(status().isCreated())).get("mapId").asText();
            return new Host(host, mapId, "{\"mapId\":\"" + mapId + "\"}");
        }

        private Host 지도까지_공개한_지도장() throws Exception {
            Host host = 공개_프로필의_지도장();
            세션으로(host.host(), put("/maps/" + host.mapId() + "/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"photoRequired\":false,\"dailyCheckInCap\":5,\"visibility\":\"PUBLIC\"}")).andExpect(status().isOk());
            return host;
        }

        private ResultActions 프로필로_합류(Anonymous guest, String handle, String body) throws Exception {
            return mvc.perform(post("/maps/join-via-profile/" + handle).header(H, guest.token()).contentType(MediaType.APPLICATION_JSON)
                .content(body));
        }

        private Anonymous 합류한_손님(Host host) throws Exception {
            Anonymous guest = 기기();
            프로필로_합류(guest, host.host().handle(), host.joinBody()).andExpect(status().isOk())
                .andExpect(jsonPath("$.mapId").value(host.mapId()));
            await().atMost(WAIT).untilAsserted(() -> {
                assertThat(가방(guest.id(), "invite:")).containsExactly("invite:guest-ticket");
                assertThat(가방(host.host().explorerId(), "invite:")).containsExactly("invite:host-flag");
            });
            return guest;
        }

        @Test
        @DisplayName("닫힌 지도는 프로필로 합류할 수 없고 프로필에도 드러나지 않는다")
        void closedMap() throws Exception {
            Host host = 공개_프로필의_지도장();
            프로필로_합류(기기(), host.host().handle(), host.joinBody()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_MAP_NOT_FOUND"));
            assertThat(html("/u/" + host.host().handle())).doesNotContain(host.mapId());
        }

        @Test
        @DisplayName("지도를 공개하면 프로필에 합류 버튼이 보이고 초대코드는 드러나지 않는다")
        void openMapShowsJoin() throws Exception {
            Host host = 지도까지_공개한_지도장();
            assertThat(html("/u/" + host.host().handle())).contains("data-join=\"" + host.mapId() + "\"").doesNotContain("inviteCode");
        }

        @Test
        @DisplayName("잘못된 지도나 없는 사람의 프로필로는 합류할 수 없다")
        void wrongMapOrPerson() throws Exception {
            Host host = 지도까지_공개한_지도장();
            Anonymous guest = 기기();
            프로필로_합류(guest, host.host().handle(), "{\"mapId\":\"not-a-uuid\"}").andExpect(status().isNotFound());
            프로필로_합류(guest, "nobody", host.joinBody()).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("처음 합류하면 양쪽이 초대 보상을 받고 같은 사이의 초대는 한 번으로 기록된다")
        void firstJoinRewardsBoth() throws Exception {
            Host host = 지도까지_공개한_지도장();
            Anonymous guest = 합류한_손님(host);
            assertThat(jdbc.queryForObject("SELECT source FROM owned_item WHERE explorer_id = ? AND item_id = 'invite:guest-ticket'",
                String.class, guest.id())).isEqualTo("EVENT");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invite_reward WHERE invitee_id = ? AND inviter_id = ?", Integer.class,
                guest.id(), host.host().explorerId())).isEqualTo(1);
        }

        @Test
        @DisplayName("떠났다가 유예 안에 다시 합류하면 보상을 다시 주지 않는다")
        void rejoinGivesNoReward() throws Exception {
            Host host = 지도까지_공개한_지도장();
            Anonymous guest = 합류한_손님(host);
            mvc.perform(post("/maps/" + host.mapId() + "/leave").header(H, guest.token())).andExpect(status().isOk());
            clock.advance(Duration.ofMinutes(1));
            프로필로_합류(guest, host.host().handle(), host.joinBody()).andExpect(status().isOk());
            await().atMost(WAIT).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE published_at IS NULL",
                Integer.class) == 0);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invite_reward WHERE invitee_id = ?", Integer.class, guest.id()))
                .isEqualTo(1);
        }

        @Test
        @DisplayName("지도장 자신은 이미 멤버라 자기 프로필로 합류할 수 없다")
        void hostCannotInviteSelf() throws Exception {
            Host host = 지도까지_공개한_지도장();
            세션으로(host.host(), post("/maps/join-via-profile/" + host.host().handle()).contentType(MediaType.APPLICATION_JSON)
                .content(host.joinBody())).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("프로필을 비공개로 돌리면 지도가 공개여도 합류할 수 없고 보상도 없다")
        void privateProfileBlocksJoin() throws Exception {
            Host host = 지도까지_공개한_지도장();
            공개_범위(host.host(), "PRIVATE").andExpect(status().isOk());
            Anonymous late = 기기();
            프로필로_합류(late, host.host().handle(), host.joinBody()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_MAP_NOT_FOUND"));
            assertThat(가방(late.id(), "invite:")).isEmpty();
        }
    }

    @Nested
    @DisplayName("초대 보상")
    class InviteRewards {

        @Test
        @DisplayName("초대코드로 처음 합류해도 지도장과 새 멤버가 초대 보상을 받는다")
        void inviteCodeJoinRewardsBoth() throws Exception {
            Anonymous owner = 기기();
            JsonNode map = explorers.공유_지도를_만든다(owner, "제주 원정대");
            Anonymous friend = 기기();
            explorers.합류한다(friend, map.get("inviteCode").asText());
            await().atMost(WAIT).untilAsserted(() -> {
                assertThat(가방(friend.id(), "invite:")).containsExactly("invite:guest-ticket");
                assertThat(가방(owner.id(), "invite:")).containsExactly("invite:host-flag");
            });
        }

        @Test
        @DisplayName("익명 탐험가가 받은 초대 보상은 계정으로 합치면 계정으로 옮겨진다")
        void movesToAccountOnMerge() throws Exception {
            Session account = 로그인(기기());
            Anonymous owner = 기기();
            JsonNode map = explorers.공유_지도를_만든다(owner, "병합 보상");
            Anonymous guest = 기기();
            explorers.합류한다(guest, map.get("inviteCode").asText());
            await().atMost(WAIT).until(() -> 가방(guest.id(), "invite:").contains("invite:guest-ticket"));
            assertThat(가방(account.explorerId(), "invite:")).isEmpty();

            String email = jdbc.queryForObject("SELECT email FROM account WHERE explorer_id = ?", String.class, account.explorerId());
            Session merged = 로그인(guest, email);
            assertThat(merged.explorerId()).isEqualTo(account.explorerId());
            await().atMost(WAIT).untilAsserted(() ->
                assertThat(가방(account.explorerId(), "invite:")).containsExactly("invite:guest-ticket"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invite_reward WHERE invitee_id = ? AND inviter_id = ?", Integer.class,
                account.explorerId(), owner.id())).isEqualTo(1);
        }

        @Test
        @DisplayName("예전 합류 소식은 초대한 사람 없이 읽히고, 새 소식은 초대한 사람을 싣는다")
        void legacyJoinNews() throws Exception {
            MemberJoined legacy = om.readValue("{\"mapId\":\"m\",\"explorerId\":\"e\",\"role\":\"MEMBER\","
                + "\"joinedAt\":\"2026-10-01T00:00:00Z\",\"rejoined\":false}", MemberJoined.class);
            assertThat(legacy.invitedBy()).isNull();
            assertThat(om.readValue(om.writeValueAsString(new MemberJoined("m", "e", "MEMBER", Instant.EPOCH, false, "h")),
                MemberJoined.class).invitedBy()).isEqualTo("h");
        }
    }

    @Nested
    @DisplayName("카드가 낡았는지 판단할 때")
    class Staleness {

        @Test
        @DisplayName("합친 직후 그린 카드도 다시 계산이 끝나면 낡음으로 잡혀 10분 뒤 다시 그려진다")
        void cardDrawnRightAfterMergeBecomesStale() throws Exception {
            Anonymous first = 기기();
            칠한다(first, "KR-11010", LocalDate.now(clock), null);
            String email = 새_이메일("p21");
            Session account = 로그인(first, email);
            정리될_때까지();
            Anonymous device = 기기();
            mvc.perform(post("/dev/seed").header(H, device.token())).andExpect(status().isOk());
            정리될_때까지();
            Session merged = 로그인(device, email);
            assertThat(merged.explorerId()).isEqualTo(account.explorerId());

            세션으로(merged, get("/me/cards/territory.png")).andExpect(status().isOk());
            Instant firstRender = renderedAt(merged.explorerId(), "TERRITORY");
            정리될_때까지();
            세션으로(merged, get("/me/cards")).andExpect(jsonPath("$.cards[0].stale").value(true));
            clock.advance(Duration.ofMinutes(10));
            세션으로(merged, get("/me/cards/territory.png")).andExpect(status().isOk());
            assertThat(renderedAt(merged.explorerId(), "TERRITORY")).isAfter(firstRender);
            세션으로(merged, get("/me/cards")).andExpect(jsonPath("$.cards[0].stale").value(false));
        }

        @Test
        @DisplayName("칭호만 바꿔도 다음에 볼 때 카드가 낡았다고 잡힌다")
        void titleChangeMakesStale() throws Exception {
            Anonymous device = 기기();
            mvc.perform(post("/dev/seed").header(H, device.token())).andExpect(status().isOk());
            정리될_때까지();
            Session owner = 로그인(device);
            정리될_때까지();
            세션으로(owner, get("/me/cards/territory.png")).andExpect(status().isOk());
            세션으로(owner, get("/me/cards")).andExpect(jsonPath("$.cards[0].stale").value(false));

            JsonNode progress = json(세션으로(owner, get("/progress")));
            String other = null;
            for (JsonNode title : progress.get("titles")) {
                if (title.get("earned").asBoolean() && !title.get("selected").asBoolean()) other = title.get("id").asText();
            }
            assertThat(other).as("시드 45곳이면 고를 칭호가 있다").isNotNull();
            세션으로(owner, put("/progress/title").contentType(MediaType.APPLICATION_JSON).content("{\"titleId\":\"" + other + "\"}"))
                .andExpect(status().isOk());
            세션으로(owner, get("/me/cards")).andExpect(jsonPath("$.cards[0].stale").value(true));
        }

        @Test
        @DisplayName("로그인 직후 공개 카드는 익명 때 그린 카드가 아니라 handle이 담긴 카드로 10분을 기다리지 않고 다시 그려진다")
        void handleChangeRedrawsImmediately() throws Exception {
            Anonymous device = 기기();
            칠한다(device, "KR-11010", LocalDate.now(clock), null);
            정리될_때까지();
            mvc.perform(get("/me/cards/territory.png").header(H, device.token())).andExpect(status().isOk());
            assertThat(renderedHandle(device.id(), "TERRITORY")).isNull();
            Instant anonymousRender = renderedAt(device.id(), "TERRITORY");

            Session owner = 로그인(device);
            공개한다(owner);
            clock.advance(Duration.ofSeconds(5));
            mvc.perform(get("/u/" + owner.handle() + "/card/territory.png")).andExpect(status().isOk());
            assertThat(renderedHandle(owner.explorerId(), "TERRITORY")).isEqualTo(owner.handle());
            assertThat(renderedAt(owner.explorerId(), "TERRITORY")).isAfter(anonymousRender);
        }
    }

    @Nested
    @DisplayName("내 연간 리캡")
    class Recap {

        private JsonNode recap(Anonymous who, String query) throws Exception {
            return json(mvc.perform(get("/me/recap" + query).header(H, who.token())).andExpect(status().isOk()));
        }

        /** 올해 중구 → 종로(같은 날, 칠한 순서는 중구가 먼저), 작년 6월 울릉. */
        private Anonymous 올해_두_곳_작년_한_곳() throws Exception {
            Anonymous me = 기기();
            LocalDate today = LocalDate.now(clock);
            칠한다(me, "KR-11020", today, MEMO);
            칠한다(me, "KR-11010", today, MEMO);
            칠한다(me, "KR-37430", today.minusYears(1).withMonth(6).withDayOfMonth(1), MEMO);
            return me;
        }

        @Test
        @DisplayName("기본은 개인 지도의 올해 — 새로 칠한 곳·달별 수·최다 시·도·가장 희귀한 곳·새 시·도·최다 달을 보여 준다")
        void thisYearOnPersonalMap() throws Exception {
            Anonymous me = 올해_두_곳_작년_한_곳();
            LocalDate today = LocalDate.now(clock);
            JsonNode recap = recap(me, "");
            assertThat(recap.get("year").asInt()).isEqualTo(today.getYear());
            assertThat(recap.get("mapId").asText()).isEqualTo(me.personalMapId());
            assertThat(recap.get("newRegions").asInt()).isEqualTo(2);
            assertThat(recap.get("monthCounts")).hasSize(12);
            assertThat(recap.get("monthCounts").get(today.getMonthValue() - 1).asInt()).isEqualTo(2);
            assertThat(recap.get("topProvince").get("provinceCode").asText()).isEqualTo("KR-11");
            assertThat(recap.get("topProvince").get("provinceName").asText()).isEqualTo("서울");
            assertThat(recap.get("topProvince").get("count").asInt()).isEqualTo(2);
            assertThat(recap.get("rarest").get("regionCode").asText()).as("동점은 지역 코드 순").isEqualTo("KR-11010");
            assertThat(recap.get("rarest").get("rarity").asText()).isEqualTo("COMMON");
            assertThat(recap.get("newProvinces").asInt()).as("서울 — 경북은 작년 방문").isEqualTo(1);
            assertThat(recap.get("busiestMonth").get("month").asInt()).isEqualTo(today.getMonthValue());
            assertThat(recap.get("busiestMonth").get("count").asInt()).isEqualTo(2);
            assertThat(recap.get("setsCompleted").asInt()).isZero();
        }

        @Test
        @DisplayName("메모·사진·정확한 날짜는 담기지 않는다")
        void noPrivateDetails() throws Exception {
            Anonymous me = 올해_두_곳_작년_한_곳();
            assertThat(recap(me, "").toString()).doesNotContain(MEMO).doesNotContain("photo")
                .doesNotContain(LocalDate.now(clock).toString());
        }

        @Test
        @DisplayName("다른 해를 고르면 그 해에 칠한 곳으로 계산한다")
        void otherYear() throws Exception {
            Anonymous me = 올해_두_곳_작년_한_곳();
            int lastYear = LocalDate.now(clock).getYear() - 1;
            JsonNode previous = recap(me, "?year=" + lastYear + "&mapId=" + me.personalMapId());
            assertThat(previous.get("newRegions").asInt()).isEqualTo(1);
            assertThat(previous.get("rarest").get("regionCode").asText()).isEqualTo("KR-37430");
            assertThat(previous.get("rarest").get("rarity").asText()).isEqualTo("LEGEND");
            assertThat(previous.get("newProvinces").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("칠한 곳이 없는 해는 비어 있다")
        void emptyYear() throws Exception {
            JsonNode empty = recap(올해_두_곳_작년_한_곳(), "?year=2001");
            assertThat(empty.get("newRegions").asInt()).isZero();
            assertThat(empty.get("topProvince").isNull()).isTrue();
            assertThat(empty.get("rarest").isNull()).isTrue();
            assertThat(empty.get("busiestMonth").isNull()).isTrue();
        }

        @Test
        @DisplayName("리캡 카드 그림과 같은 계산 값이다")
        void sameAsRecapCard() throws Exception {
            Anonymous me = 올해_두_곳_작년_한_곳();
            LocalDate today = LocalDate.now(clock);
            JsonNode recap = recap(me, "");
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

        @Nested
        @DisplayName("공유 지도를 고르면")
        class SharedMap {

            record Crew(Anonymous owner, Anonymous friend, String mapId) {}

            private Crew 함께_칠한_지도() throws Exception {
                Anonymous owner = 기기();
                Anonymous friend = 기기();
                JsonNode map = explorers.공유_지도를_만든다(owner, "리캡 원정대");
                String mapId = map.get("mapId").asText();
                explorers.합류한다(friend, map.get("inviteCode").asText());
                LocalDate today = LocalDate.now(clock);
                칠한다(owner, "KR-11010", today, MEMO);
                explorers.체크인(owner, 방문("KR-26010", today, MEMO, mapId)).andExpect(status().isCreated());
                explorers.체크인(owner, 방문("KR-26020", today, MEMO, mapId)).andExpect(status().isCreated());
                explorers.체크인(friend, 방문("KR-37430", today, MEMO, mapId)).andExpect(status().isCreated());
                return new Crew(owner, friend, mapId);
            }

            @Test
            @DisplayName("그 지도에서 내가 칠한 곳만 센다")
            void countsOnlyMine() throws Exception {
                Crew crew = 함께_칠한_지도();
                JsonNode shared = recap(crew.owner(), "?mapId=" + crew.mapId());
                assertThat(shared.get("mapId").asText()).isEqualTo(crew.mapId());
                assertThat(shared.get("newRegions").asInt()).isEqualTo(2);
                assertThat(shared.get("topProvince").get("provinceCode").asText()).isEqualTo("KR-26");
                assertThat(shared.get("rarest").get("regionCode").asText()).isEqualTo("KR-26010");
                JsonNode personal = recap(crew.owner(), "");
                assertThat(personal.get("newRegions").asInt()).isEqualTo(1);
                assertThat(personal.get("topProvince").get("provinceCode").asText()).isEqualTo("KR-11");
                assertThat(recap(crew.friend(), "?mapId=" + crew.mapId()).get("rarest").get("rarity").asText()).isEqualTo("LEGEND");
            }

            @Test
            @DisplayName("멤버가 아니면 그 지도의 리캡을 볼 수 없다")
            void nonMember() throws Exception {
                Crew crew = 함께_칠한_지도();
                mvc.perform(get("/me/recap").param("mapId", crew.mapId()).header(H, 기기().token()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
            }

            @Test
            @DisplayName("없는 지도는 없다고 알린다")
            void unknownMap() throws Exception {
                mvc.perform(get("/me/recap").param("mapId", UUID.randomUUID().toString()).header(H, 기기().token()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MAP_NOT_FOUND"));
            }
        }

        @Test
        @DisplayName("연도가 잘못되면 거절된다")
        void invalidYear() throws Exception {
            Anonymous owner = 기기();
            mvc.perform(get("/me/recap").param("year", "0").header(H, owner.token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_YEAR"));
            mvc.perform(get("/me/recap").param("year", "올해").header(H, owner.token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_PARAMETER"));
        }

        @Test
        @DisplayName("누구인지 모르면 볼 수 없다")
        void needsExplorer() throws Exception {
            mvc.perform(get("/me/recap")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EXPLORER_TOKEN_REQUIRED"));
        }
    }
}
