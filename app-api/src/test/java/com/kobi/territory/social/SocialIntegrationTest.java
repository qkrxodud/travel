package com.kobi.territory.social;

import static com.kobi.territory.support.Explorers.새_이메일;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.outbox.OutboxRelay;
import com.kobi.territory.social.application.FeedProjector;
import com.kobi.territory.social.application.SocialSubscriptions;
import com.kobi.territory.social.domain.friendship.Friendship;
import com.kobi.territory.social.domain.friendship.FriendshipAlreadyExists;
import com.kobi.territory.social.domain.friendship.FriendshipRepository;
import com.kobi.territory.support.Explorers;
import com.kobi.territory.support.Explorers.Anonymous;
import com.kobi.territory.support.Explorers.Session;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 5단계 소셜 D2·D3: 팔로우 규칙, 주인 본인 미리보기, 맞팔 시 FRIENDS 프로필·카드·VS, 영토 비교, 이벤트 → 친구 소식 투영(공개 범위·취소 거둠·멱등·
 * 재구성), 병합 귀속, 지도 안 랭킹(이의·탈퇴 숨김 제외), 친구 랭킹(탐험가 단위 중복 제거), 상위 % 배치·지역 통계·콜드 스타트.
 * 같은 컨텍스트(H2)를 다른 통합 테스트와 함께 쓰므로 전체 수(모집단)에 기대지 않고 상대적인 값만 본다.
 * 회귀 출처: QA P3-3(숨은 대상 404·언팔 멱등), P2-5(병합 소식 취소), Q1(이의 다음 선점), 리더 결정 1(PRIVATE 맞팔 숨김)·2(주인 미리보기)·
 * 5(모집단), r2 P2-A(숨은 팔로우 관측 불가)·P2-B(재구성 중 취소 범위)·P3-A(재생 실패)·P3-B(병합 탐험가 통계 제외).
 */
@IntegrationTest
@DisplayName("친구와 소식과 랭킹")
class SocialIntegrationTest {

    private static final String H = Explorers.TOKEN;
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired FeedProjector projector;
    @Autowired OutboxRelay relay;
    @Autowired FriendshipRepository friendshipRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Explorers explorers;

    // ---- 준비 문장 ---------------------------------------------------------------------------------------------

    private Anonymous 기기() throws Exception {
        return explorers.익명_탐험가();
    }

    private Session 로그인() throws Exception {
        return explorers.로그인(null, 새_이메일("social"));
    }

    private Session 로그인(Anonymous device, String email) throws Exception {
        return explorers.로그인(device, email);
    }

    private ResultActions 세션으로(Session session, MockHttpServletRequestBuilder builder) throws Exception {
        return explorers.세션으로(session, builder);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return explorers.json(result);
    }

    private void 공개_범위(Session session, String visibility) throws Exception {
        explorers.공개_범위(session, visibility);
    }

    /** 보이는 대상(또는 나를 팔로우하는 대상) 팔로우 → 팔로우됨. */
    private void 팔로우(Session who, Session whom) throws Exception {
        세션으로(who, post("/friends/" + whom.handle())).andExpect(status().isCreated());
    }

    /** 숨은 대상(PRIVATE·친구 아닌 FRIENDS) 팔로우 → 없는 handle 과 같은 응답, 팔로우는 기록된다. */
    private void 숨은_대상_팔로우(Session who, Session whom) throws Exception {
        세션으로(who, post("/friends/" + whom.handle())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
    }

    private void 칠한다(Session who, String code, String mapId) throws Exception {
        explorers.칠한다(who, mapId, code);
    }

    private void 칠한다(Anonymous who, String code) throws Exception {
        explorers.칠한다(who, code);
    }

    private void 정리될_때까지() {
        explorers.전달이_끝날_때까지();
    }

    private List<String> 소식_지역(Session viewer) throws Exception {
        JsonNode items = json(세션으로(viewer, get("/feed")).andExpect(status().isOk())).get("items");
        List<String> regions = new ArrayList<>();
        items.forEach(item -> {
            if ("VISIT".equals(item.get("kind").asText())) regions.add(item.get("handle").asText() + ":" + item.get("regionCode").asText());
        });
        return regions;
    }

    /** 지금 세대의 그 탐험가 소식 행 수. */
    private int 소식_행(String actorId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM feed_entry WHERE actor_id = ? AND generation = "
            + "(SELECT live_generation FROM feed_state WHERE id = 1)", Integer.class, actorId);
    }

    private int 지금_소식_행() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM feed_entry WHERE generation = (SELECT live_generation FROM feed_state WHERE id = 1)",
            Integer.class);
    }

    private int 지금_세대() {
        return jdbc.queryForObject("SELECT live_generation FROM feed_state WHERE id = 1", Integer.class);
    }

    private static String 없는_주소() {
        return "nobody-" + UUID.randomUUID().toString().substring(0, 5);
    }

    // ---- 팔로우 ----------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("팔로우")
    class Follow {

        record Pair(Session kim, Session lee) {}

        /** 김과, 공개 프로필인 이. */
        private Pair 김과_공개한_이() throws Exception {
            Session kim = 로그인();
            Session lee = 로그인();
            공개_범위(lee, "PUBLIC");
            return new Pair(kim, lee);
        }

        @Test
        @DisplayName("공개 주소로 팔로우하면 앞의 @와 대소문자를 가리지 않고, 상대의 내부 식별자는 드러나지 않는다")
        void followByHandle() throws Exception {
            Pair p = 김과_공개한_이();
            세션으로(p.kim(), post("/friends/@" + p.lee().handle().toUpperCase())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.handle").value(p.lee().handle())).andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.mutual").value(false)).andExpect(jsonPath("$.explorerId").doesNotExist());
        }

        @Test
        @DisplayName("나를 팔로우한 사람을 팔로우하면 맞팔로우가 된다")
        void followBackIsMutual() throws Exception {
            Pair p = 김과_공개한_이();
            팔로우(p.kim(), p.lee());
            세션으로(p.lee(), post("/friends/" + p.kim().handle())).andExpect(status().isCreated()).andExpect(jsonPath("$.mutual").value(true));
        }

        @Test
        @DisplayName("친구 목록에 맞팔로우 수와 사람들이 보이고 내부 식별자는 드러나지 않는다")
        void friendsList() throws Exception {
            Pair p = 김과_공개한_이();
            팔로우(p.kim(), p.lee());
            팔로우(p.lee(), p.kim());
            세션으로(p.kim(), get("/friends")).andExpect(status().isOk()).andExpect(jsonPath("$.loggedIn").value(true))
                .andExpect(jsonPath("$.mutualCount").value(1)).andExpect(jsonPath("$.people[0].handle").value(p.lee().handle()))
                .andExpect(jsonPath("$.people[0].mutual").value(true)).andExpect(jsonPath("$.people[0].explorerId").doesNotExist());
        }

        @Test
        @DisplayName("익명이면 친구 목록이 비어 있다")
        void anonymousHasNoFriends() throws Exception {
            mvc.perform(get("/friends").header(H, 기기().token())).andExpect(jsonPath("$.loggedIn").value(false))
                .andExpect(jsonPath("$.people.length()").value(0));
        }

        @Test
        @DisplayName("언팔로우는 여러 번 해도, 없는 사람에게 해도 같은 답이다")
        void unfollowIsIdempotent() throws Exception {
            Pair p = 김과_공개한_이();
            팔로우(p.kim(), p.lee());
            세션으로(p.kim(), delete("/friends/" + p.lee().handle())).andExpect(status().isNoContent());
            세션으로(p.kim(), delete("/friends/" + p.lee().handle())).andExpect(status().isNoContent());
            세션으로(p.kim(), delete("/friends/nobody-zz")).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("한쪽으로 팔로우한 상대가 비공개면 내 목록에서도 빠지고, 상대가 공개하면 보인다")
        void oneWayToPrivateIsHiddenFromList() throws Exception {
            Pair p = 김과_공개한_이();
            팔로우(p.kim(), p.lee());
            팔로우(p.lee(), p.kim());
            세션으로(p.kim(), delete("/friends/" + p.lee().handle())).andExpect(status().isNoContent());
            세션으로(p.lee(), get("/friends")).andExpect(jsonPath("$.mutualCount").value(0)).andExpect(jsonPath("$.people.length()").value(0));
            공개_범위(p.kim(), "PUBLIC");
            세션으로(p.lee(), get("/friends")).andExpect(jsonPath("$.people[0].following").value(true))
                .andExpect(jsonPath("$.people[0].follower").value(false));
        }

        @Nested
        @DisplayName("팔로우할 수 없을 때")
        class Rejections {

            @Test
            @DisplayName("로그인하지 않으면 팔로우할 수 없다")
            void loginRequired() throws Exception {
                Pair p = 김과_공개한_이();
                mvc.perform(post("/friends/" + p.kim().handle()).header(H, 기기().token())).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
            }

            @Test
            @DisplayName("없는 사람은 팔로우할 수 없다")
            void unknownPerson() throws Exception {
                세션으로(로그인(), post("/friends/" + 없는_주소())).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
            }

            @Test
            @DisplayName("자기 자신은 팔로우할 수 없다")
            void notSelf() throws Exception {
                Session kim = 로그인();
                세션으로(kim, post("/friends/" + kim.handle())).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"));
            }

            @Test
            @DisplayName("이미 팔로우한 사람은 다시 팔로우할 수 없다")
            void alreadyFollowing() throws Exception {
                Pair p = 김과_공개한_이();
                팔로우(p.kim(), p.lee());
                세션으로(p.kim(), post("/friends/" + p.lee().handle())).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_FOLLOWING"));
            }
        }
    }

    @Nested
    @DisplayName("숨은 프로필을 팔로우하면")
    class HiddenFollow {

        @Test
        @DisplayName("없는 사람을 팔로우한 것과 같은 답을 받고, 다시 해도 같으며 내 목록에도 드러나지 않는다")
        void looksLikeUnknownPerson() throws Exception {
            Session kim = 로그인();
            Session secret = 로그인();
            String unknownBody = 세션으로(kim, post("/friends/" + 없는_주소()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            String hiddenBody = 세션으로(kim, post("/friends/" + secret.handle())).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
            assertThat(hiddenBody).isEqualTo(unknownBody);
            숨은_대상_팔로우(kim, secret);
            세션으로(kim, get("/friends")).andExpect(jsonPath("$.people.length()").value(0));
        }

        @Test
        @DisplayName("팔로우는 기록되어 상대에게는 나를 팔로우한 사람으로 보이고, 상대가 맞팔하면 친구가 된다")
        void recordedAndMutualOnFollowBack() throws Exception {
            Session kim = 로그인();
            Session secret = 로그인();
            숨은_대상_팔로우(kim, secret);
            세션으로(secret, get("/friends")).andExpect(jsonPath("$.people[0].handle").value(kim.handle()))
                .andExpect(jsonPath("$.people[0].follower").value(true)).andExpect(jsonPath("$.people[0].following").value(false));
            세션으로(secret, post("/friends/" + kim.handle())).andExpect(status().isCreated()).andExpect(jsonPath("$.mutual").value(true));
            세션으로(kim, get("/friends")).andExpect(jsonPath("$.mutualCount").value(1)).andExpect(jsonPath("$.people[0].mutual").value(true));
        }

        @Test
        @DisplayName("친구가 된 뒤 상대가 친구 공개로 바꾸면 존재가 드러나 다시 팔로우는 중복으로 거절된다")
        void friendsVisibilityRevealsDuplicate() throws Exception {
            Session kim = 로그인();
            Session secret = 로그인();
            숨은_대상_팔로우(kim, secret);
            팔로우(secret, kim);
            공개_범위(secret, "FRIENDS");
            세션으로(kim, post("/friends/" + secret.handle())).andExpect(status().isConflict());
        }

        @Nested
        @DisplayName("없는 사람과 구별할 수 없다")
        class Indistinguishable {

            /** 내가 관측할 수 있는 값들(응답 본문 그대로). */
            private List<String> 관측값(Session who) throws Exception {
                List<String> out = new ArrayList<>();
                for (String path : List.of("/feed", "/friends", "/rankings/friends")) {
                    out.add(세션으로(who, get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
                }
                return out;
            }

            @Test
            @DisplayName("숨은 사람 팔로우와 없는 사람 팔로우는 답이 글자 하나까지 같다")
            void sameResponse() throws Exception {
                Session hidden = 로그인();
                MvcResult hiddenResult = 세션으로(로그인(), post("/friends/" + hidden.handle())).andReturn();
                MvcResult missingResult = 세션으로(로그인(), post("/friends/" + 없는_주소())).andReturn();
                assertThat(hiddenResult.getResponse().getStatus()).isEqualTo(404).isEqualTo(missingResult.getResponse().getStatus());
                assertThat(hiddenResult.getResponse().getContentAsString()).isEqualTo(missingResult.getResponse().getContentAsString());
                assertThat(hiddenResult.getResponse().getHeaderNames()).isEqualTo(missingResult.getResponse().getHeaderNames());
            }

            @Test
            @DisplayName("숨은 팔로우는 이후 소식·친구·랭킹 어디에도 흔적을 남기지 않는다")
            void leavesNoTrace() throws Exception {
                Session hidden = 로그인();
                Session prober = 로그인();
                List<String> before = 관측값(prober);
                세션으로(prober, post("/friends/" + hidden.handle())).andExpect(status().isNotFound());
                assertThat(관측값(prober)).isEqualTo(before);
                assertThat(json(세션으로(prober, get("/feed"))).get("followingCount").asInt()).isZero();
                세션으로(prober, post("/friends/" + 없는_주소())).andExpect(status().isNotFound());
                assertThat(관측값(prober)).isEqualTo(before);
            }

            @Test
            @DisplayName("익명이면 있는 사람이든 없는 사람이든 같은 로그인 요구를 받는다")
            void anonymousGetsSameAnswer() throws Exception {
                Session hidden = 로그인();
                Anonymous anonymous = 기기();
                String anonHidden = mvc.perform(post("/friends/" + hidden.handle()).header(H, anonymous.token()))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
                String anonMissing = mvc.perform(post("/friends/" + 없는_주소()).header(H, anonymous.token()))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
                assertThat(anonHidden).isEqualTo(anonMissing).contains("LOGIN_REQUIRED");
            }

            @Test
            @DisplayName("동시에 여러 번 보내도 모두 없는 사람과 같은 답이다")
            void concurrentRequestsSameAnswer() throws Exception {
                Session hidden = 로그인();
                Session racer = 로그인();
                ExecutorService pool = Executors.newFixedThreadPool(4);
                try {
                    List<Callable<Integer>> calls = new ArrayList<>();
                    for (int i = 0; i < 4; i++) {
                        calls.add(() -> 세션으로(racer, post("/friends/" + hidden.handle())).andReturn().getResponse().getStatus());
                    }
                    List<Integer> statuses = new ArrayList<>();
                    for (Future<Integer> future : pool.invokeAll(calls)) statuses.add(future.get());
                    assertThat(statuses).containsOnly(404);
                } finally {
                    pool.shutdownNow();
                }
            }

            @Test
            @DisplayName("이미 있는 팔로우를 다시 저장하려 하면 중복으로 알려 숨은 대상이면 없는 사람 답으로 바꿀 수 있다")
            void duplicateSaveIsReported() throws Exception {
                Session hidden = 로그인();
                Session racer = 로그인();
                세션으로(racer, post("/friends/" + hidden.handle())).andExpect(status().isNotFound());
                assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> friendshipRepository.add(
                    Friendship.restore(ExplorerId.of(racer.explorerId()), ExplorerId.of(hidden.explorerId()), Instant.now()))))
                    .isInstanceOf(FriendshipAlreadyExists.class);
            }

            @Test
            @DisplayName("그래도 숨은 사람에게는 팔로우한 사람들이 보인다")
            void targetSeesFollowers() throws Exception {
                Session hidden = 로그인();
                세션으로(로그인(), post("/friends/" + hidden.handle())).andExpect(status().isNotFound());
                세션으로(로그인(), post("/friends/" + hidden.handle())).andExpect(status().isNotFound());
                세션으로(hidden, get("/friends")).andExpect(jsonPath("$.people.length()").value(2));
            }
        }
    }

    // ---- 친구 공개 범위 · 비교 -------------------------------------------------------------------------------

    @Nested
    @DisplayName("친구 공개 프로필")
    class FriendsOnlyProfile {

        record Trio(Session owner, Session friend, Session stranger) {}

        /** 주인(종로, 친구 공개)·친구(중구, 주인을 팔로우 중)·모르는 사람. */
        private Trio 친구_공개한_주인() throws Exception {
            Session owner = 로그인();
            Session friend = 로그인();
            Session stranger = 로그인();
            칠한다(owner, "KR-11010", null);
            칠한다(friend, "KR-11020", null);
            정리될_때까지();
            공개_범위(owner, "FRIENDS");
            숨은_대상_팔로우(friend, owner);
            return new Trio(owner, friend, stranger);
        }

        private Trio 맞팔한_친구() throws Exception {
            Trio t = 친구_공개한_주인();
            팔로우(t.owner(), t.friend());
            return t;
        }

        @Test
        @DisplayName("한쪽 팔로우로는 열리지 않고 영토 비교도 되지 않는다")
        void oneWayIsNotEnough() throws Exception {
            Trio t = 친구_공개한_주인();
            세션으로(t.friend(), get("/u/" + t.owner().handle())).andExpect(status().isNotFound());
            세션으로(t.friend(), get("/compare/" + t.owner().handle())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
        }

        @Test
        @DisplayName("맞팔로우면 프로필과 카드가 열리고, 그 응답은 보는 사람 전용으로 다른 사람과 공유되지 않는다")
        void mutualOpens() throws Exception {
            Trio t = 맞팔한_친구();
            세션으로(t.friend(), get("/u/" + t.owner().handle())).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control")).contains("private"))
                .andExpect(result -> assertThat(result.getResponse().getHeaders("Vary")).contains("Cookie"));
            세션으로(t.friend(), get("/u/" + t.owner().handle() + "/card/territory.png")).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control")).contains("private"));
        }

        @Test
        @DisplayName("맞팔로우가 아닌 사람·익명 방문자·풀리지 않는 토큰에게는 없는 것처럼 보인다")
        void othersSeeNothing() throws Exception {
            Trio t = 맞팔한_친구();
            세션으로(t.stranger(), get("/u/" + t.owner().handle())).andExpect(status().isNotFound());
            mvc.perform(get("/u/" + t.owner().handle())).andExpect(status().isNotFound());
            mvc.perform(get("/u/" + t.owner().handle()).header(H, "not-a-token")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("맞팔로우면 영토를 나만·둘 다·상대만으로 나눠 비교한다")
        void mutualCanCompare() throws Exception {
            Trio t = 맞팔한_친구();
            세션으로(t.friend(), get("/compare/" + t.owner().handle())).andExpect(status().isOk()).andExpect(jsonPath("$.mutual").value(true))
                .andExpect(jsonPath("$.onlyMine[0]").value("KR-11020")).andExpect(jsonPath("$.onlyTheirs[0]").value("KR-11010"))
                .andExpect(jsonPath("$.both.length()").value(0)).andExpect(jsonPath("$.me.regionCount").value(1))
                .andExpect(jsonPath("$.other.handle").value(t.owner().handle())).andExpect(jsonPath("$.lead").value(0));
        }

        @Test
        @DisplayName("자기 자신과는 비교할 수 없다")
        void notWithSelf() throws Exception {
            Trio t = 맞팔한_친구();
            세션으로(t.friend(), get("/compare/" + t.friend().handle())).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CANNOT_COMPARE_SELF"));
        }

        @Test
        @DisplayName("주인이 언팔로우하면 다시 닫혀 프로필도 비교도 없는 것처럼 보인다")
        void unfollowCloses() throws Exception {
            Trio t = 맞팔한_친구();
            세션으로(t.owner(), delete("/friends/" + t.friend().handle())).andExpect(status().isNoContent());
            세션으로(t.friend(), get("/u/" + t.owner().handle())).andExpect(status().isNotFound());
            세션으로(t.friend(), get("/compare/" + t.owner().handle())).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("공개 프로필이면 팔로우 없이도 비교할 수 있다")
        void publicCanBeCompared() throws Exception {
            Trio t = 친구_공개한_주인();
            공개_범위(t.owner(), "PUBLIC");
            세션으로(t.stranger(), get("/compare/" + t.owner().handle())).andExpect(status().isOk()).andExpect(jsonPath("$.mutual").value(false));
        }
    }

    @Nested
    @DisplayName("프로필 주인 본인은")
    class OwnerPreview {

        record Pair(Session owner, Session other) {}

        private Pair 주인과_공개한_상대() throws Exception {
            Session owner = 로그인();
            Session other = 로그인();
            칠한다(owner, "KR-11030", null);
            칠한다(other, "KR-11040", null);
            정리될_때까지();
            공개_범위(other, "PUBLIC");
            return new Pair(owner, other);
        }

        @Test
        @DisplayName("비공개·친구 공개여도 자기 프로필·카드·비교 카드를 본인 전용 응답으로 본다")
        void ownerAlwaysSees() throws Exception {
            Pair p = 주인과_공개한_상대();
            for (String visibility : List.of("PRIVATE", "FRIENDS")) {
                공개_범위(p.owner(), visibility);
                세션으로(p.owner(), get("/u/" + p.owner().handle())).andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control")).contains("private"));
                세션으로(p.owner(), get("/u/" + p.owner().handle() + "/card/territory.png")).andExpect(status().isOk());
                세션으로(p.owner(), get("/u/" + p.owner().handle() + "/vs/" + p.other().handle() + ".png")).andExpect(status().isOk());
                세션으로(p.owner(), get("/u/" + p.other().handle() + "/vs/" + p.owner().handle() + ".png")).andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("그래도 방문자와 상대에게는 여전히 보이지 않는다")
        void othersStillCannot() throws Exception {
            Pair p = 주인과_공개한_상대();
            for (String visibility : List.of("PRIVATE", "FRIENDS")) {
                공개_범위(p.owner(), visibility);
                mvc.perform(get("/u/" + p.owner().handle())).andExpect(status().isNotFound());
                세션으로(p.other(), get("/u/" + p.other().handle() + "/vs/" + p.owner().handle() + ".png")).andExpect(status().isNotFound());
            }
        }
    }

    // ---- 친구 소식 ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("친구 소식")
    class Feed {

        /** 나 + 공개(한쪽 팔로우) + 친구 공개(맞팔) + 친구 공개(한쪽) + 비공개(맞팔), 각자 칠함 + 공개한 사람이 한 곳 더. */
        record Circle(Session me, Session open, Session friendsOnly, Session oneWay, Session hidden) {}

        private Circle 다섯_사람의_소식() throws Exception {
            Session me = 로그인();
            Session open = 로그인();
            Session friendsOnly = 로그인();
            Session oneWay = 로그인();
            Session hidden = 로그인();
            공개_범위(open, "PUBLIC");
            공개_범위(friendsOnly, "FRIENDS");
            공개_범위(oneWay, "FRIENDS");
            팔로우(me, open);
            숨은_대상_팔로우(friendsOnly, me);
            팔로우(me, friendsOnly);
            숨은_대상_팔로우(me, oneWay);
            숨은_대상_팔로우(me, hidden);
            팔로우(hidden, me);
            칠한다(open, "KR-26010", null);
            칠한다(friendsOnly, "KR-26020", null);
            칠한다(oneWay, "KR-26030", null);
            칠한다(hidden, "KR-26040", null);
            칠한다(open, "KR-26310", null);
            정리될_때까지();
            return new Circle(me, open, friendsOnly, oneWay, hidden);
        }

        @Test
        @DisplayName("공개 범위가 허락하는 사람의 소식만 최근 순으로 보인다 — 공개는 한쪽 팔로우에도, 친구 공개는 맞팔에게만, 비공개는 아무에게도")
        void visibilityFilters() throws Exception {
            Circle c = 다섯_사람의_소식();
            assertThat(소식_지역(c.me())).containsExactly(c.open().handle() + ":KR-26310", c.friendsOnly().handle() + ":KR-26020",
                c.open().handle() + ":KR-26010");
        }

        @Test
        @DisplayName("소식은 오늘·어제 같은 상대 시각으로만 보이고 정확한 시각은 없다")
        void relativeTimeOnly() throws Exception {
            Circle c = 다섯_사람의_소식();
            JsonNode first = json(세션으로(c.me(), get("/feed"))).get("items").get(0);
            assertThat(first.get("when").asText()).isEqualTo("오늘");
            assertThat(first.has("visitedAt")).isFalse();
            assertThat(first.get("rarity").asText()).isNotBlank();
        }

        @Test
        @DisplayName("칠한 곳을 취소하면 그 소식을 거둔다")
        void cancelRetracts() throws Exception {
            Circle c = 다섯_사람의_소식();
            세션으로(c.open(), delete("/visits/KR-26310")).andExpect(status().isNoContent());
            정리될_때까지();
            assertThat(소식_지역(c.me())).containsExactly(c.friendsOnly().handle() + ":KR-26020", c.open().handle() + ":KR-26010");
        }

        @Test
        @DisplayName("같은 소식이 두 번 와도 한 번만 쌓인다")
        void duplicateIsOnce() throws Exception {
            Circle c = 다섯_사람의_소식();
            int before = 소식_행(c.open().explorerId());
            RegionVisited duplicate = new RegionVisited(c.open().explorerId(), c.open().personalMapId(), "KR-26010", Rarity.COMMON,
                "KR-26", clock.instant(), LocalDate.now(clock), true, 1, true, 1, List.of(c.open().explorerId()));
            projector.project(duplicate);
            projector.project(duplicate);
            assertThat(소식_행(c.open().explorerId())).isEqualTo(before);
        }

        @Test
        @DisplayName("소식을 처음부터 다시 만들어도 거둔 소식은 거둔 채 같은 결과이고, 이전 판은 지운다")
        void rebuildIsSame() throws Exception {
            Circle c = 다섯_사람의_소식();
            세션으로(c.open(), delete("/visits/KR-26310")).andExpect(status().isNoContent());
            정리될_때까지();
            List<String> beforeRebuild = 소식_지역(c.me());
            int rowsBefore = 지금_소식_행();
            int generationBefore = 지금_세대();
            JsonNode rebuilt = json(mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk()));
            assertThat(rebuilt.get("state").asText()).isEqualTo("SUCCEEDED");
            assertThat(rebuilt.get("replayed").asInt()).isPositive();
            assertThat(rebuilt.get("skipped").asInt()).isZero();
            assertThat(지금_세대()).isEqualTo(generationBefore + 1);
            assertThat(소식_지역(c.me())).isEqualTo(beforeRebuild);
            assertThat(지금_소식_행()).isEqualTo(rowsBefore);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_entry WHERE generation <> (SELECT live_generation FROM feed_state)",
                Integer.class)).as("옛 세대 정리").isZero();
            mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk());
            assertThat(지금_소식_행()).isEqualTo(rowsBefore);
        }

        @Test
        @DisplayName("공개 범위를 바꾸면 다시 만들지 않아도 바로 반영된다")
        void visibilityChangeAppliesOnRead() throws Exception {
            Circle c = 다섯_사람의_소식();
            공개_범위(c.hidden(), "FRIENDS");
            assertThat(소식_지역(c.me())).contains(c.hidden().handle() + ":KR-26040");
        }

        @Nested
        @DisplayName("익명 기록을 계정으로 합치면")
        class Merge {

            record Merged(Session account, Anonymous device, Session merged) {}

            private Merged 두_곳을_칠한_기기를_합친다() throws Exception {
                String email = 새_이메일("merge");
                Session account = 로그인(null, email);
                Anonymous device = 기기();
                칠한다(device, "KR-31011");
                칠한다(device, "KR-31012");
                정리될_때까지();
                assertThat(소식_행(device.id())).isPositive();
                Session merged = 로그인(device, email);
                assertThat(merged.explorerId()).isEqualTo(account.explorerId());
                정리될_때까지();
                return new Merged(account, device, merged);
            }

            @Test
            @DisplayName("익명 탐험가의 소식은 계정 탐험가의 개인 지도 소식이 되어 팔로우한 사람에게 보인다")
            void newsMovesToAccount() throws Exception {
                Merged m = 두_곳을_칠한_기기를_합친다();
                assertThat(소식_행(m.device().id())).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_entry WHERE actor_id = ? AND region_code = 'KR-31011' AND map_id = ?",
                    Integer.class, m.account().explorerId(), m.account().personalMapId())).isPositive();
                Session watcher = 로그인();
                공개_범위(m.merged(), "PUBLIC");
                팔로우(watcher, m.merged());
                assertThat(소식_지역(watcher)).contains(m.merged().handle() + ":KR-31011", m.merged().handle() + ":KR-31012");
            }

            @Test
            @DisplayName("계정이 옮겨 온 방문을 취소하면 친구 소식에서도 거두고, 다시 만들어도 같다")
            void cancelOfMovedVisitRetracts() throws Exception {
                Merged m = 두_곳을_칠한_기기를_합친다();
                Session watcher = 로그인();
                공개_범위(m.merged(), "PUBLIC");
                팔로우(watcher, m.merged());
                세션으로(m.merged(), delete("/visits/KR-31012")).andExpect(status().isNoContent());
                정리될_때까지();
                assertThat(소식_지역(watcher)).contains(m.merged().handle() + ":KR-31011").doesNotContain(m.merged().handle() + ":KR-31012");
                mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk());
                assertThat(소식_지역(watcher)).contains(m.merged().handle() + ":KR-31011").doesNotContain(m.merged().handle() + ":KR-31012");
            }
        }

        @Nested
        @DisplayName("소식을 다시 만들 때")
        class Rebuild {

            @Test
            @DisplayName("운영에서는 관리자 토큰이 없으면 시작할 수 없다")
            void adminTokenRequired() throws Exception {
                mvc.perform(post("/admin/rebuild/feed")).andExpect(status().isUnauthorized());
            }

            @Test
            @DisplayName("운영에서는 뒤에서 돌고 끝난 상태를 볼 수 있으며, 그동안 멈췄던 소식 전달이 이어진다")
            void runsInBackground() throws Exception {
                mvc.perform(post("/admin/rebuild/feed").header("X-Admin-Token", "local-admin-token")).andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.state").value("RUNNING"));
                await().atMost(WAIT).untilAsserted(() -> mvc.perform(get("/admin/rebuild/feed").header("X-Admin-Token", "local-admin-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("SUCCEEDED")));
                정리될_때까지();
            }

            @Test
            @DisplayName("다시 만드는 중에 칠함·취소·다시 칠함이 겹쳐도 다시 칠한 소식은 남고, 몇 번을 다시 만들어도 같다")
            void overlappingCancelKeepsRecheckIn() throws Exception {
                Session viewer = 로그인();
                Session actor = 로그인();
                공개_범위(actor, "PUBLIC");
                팔로우(viewer, actor);
                정리될_때까지();
                relay.pauseSubscriber(SocialSubscriptions.FEED_SUBSCRIBER);
                try {
                    칠한다(actor, "KR-39010", null);
                    clock.advance(Duration.ofSeconds(1));
                    세션으로(actor, delete("/visits/KR-39010")).andExpect(status().isNoContent());
                    칠한다(actor, "KR-39010", null);
                    await().atMost(WAIT).untilAsserted(() -> assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM outbox_delivery d JOIN outbox o ON o.id = d.event_id WHERE o.aggregate_id = ? "
                            + "AND d.subscriber = 'progression.progress' AND d.status = 'DELIVERED'", Integer.class, actor.personalMapId()))
                        .isGreaterThanOrEqualTo(3));
                    JsonNode rebuilt = json(mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk()));
                    assertThat(rebuilt.get("state").asText()).isEqualTo("SUCCEEDED");
                } finally {
                    relay.resumeSubscriber(SocialSubscriptions.FEED_SUBSCRIBER);
                }
                정리될_때까지();
                assertThat(소식_지역(viewer)).contains(actor.handle() + ":KR-39010");
                List<String> once = 소식_지역(viewer);
                mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk());
                assertThat(소식_지역(viewer)).isEqualTo(once);
                mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk());
                assertThat(소식_지역(viewer)).isEqualTo(once);
            }

            @Test
            @DisplayName("다시 재생하다 처리에 실패하면 실패로 끝내 이전 소식을 지키고, 읽을 수 없는 예전 기록은 막지 않는다")
            void failureKeepsPreviousFeed() throws Exception {
                Session viewer = 로그인();
                Session actor = 로그인();
                공개_범위(actor, "PUBLIC");
                팔로우(viewer, actor);
                칠한다(actor, "KR-39020", null);
                정리될_때까지();
                List<String> before = 소식_지역(viewer);
                int generationBefore = 지금_세대();
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
                    assertThat(지금_세대()).isEqualTo(generationBefore);
                    assertThat(소식_지역(viewer)).isEqualTo(before);
                } finally {
                    jdbc.update("DELETE FROM outbox WHERE aggregate_id IN ('poison', 'legacy')");
                }
                JsonNode ok = json(mvc.perform(post("/dev/rebuild/feed")).andExpect(status().isOk()));
                assertThat(ok.get("state").asText()).isEqualTo("SUCCEEDED");
                assertThat(소식_지역(viewer)).isEqualTo(before);
            }
        }
    }

    // ---- 랭킹 -----------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("랭킹")
    class Rankings {

        record League(Session owner, Session guest, Session leaver, String mapId) {}

        /**
         * 경북 공유 지도: 지도장은 울릉(전설 선점)·KR-37011·KR-37020(두 번째), 손님은 울릉·KR-37020(선점 → 이의 표시), 떠난 멤버는 세 곳.
         * 지도장은 개인 지도에도 KR-37011.
         */
        private League 경쟁한_지도() throws Exception {
            Session owner = 로그인();
            Session guest = 로그인();
            Session leaver = 로그인();
            JsonNode map = json(세션으로(owner, post("/maps").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"소셜 원정대\"}"))
                .andExpect(status().isCreated()));
            String mapId = map.get("mapId").asText();
            String code = map.get("inviteCode").asText();
            for (Session member : List.of(guest, leaver)) {
                세션으로(member, post("/maps/join").contentType(MediaType.APPLICATION_JSON).content("{\"inviteCode\":\"" + code + "\"}"))
                    .andExpect(status().isOk());
            }
            칠한다(owner, "KR-37430", mapId);
            칠한다(owner, "KR-37011", mapId);
            칠한다(guest, "KR-37430", mapId);
            칠한다(guest, "KR-37020", mapId);
            칠한다(owner, "KR-37020", mapId);
            칠한다(leaver, "KR-37030", mapId);
            칠한다(leaver, "KR-37040", mapId);
            칠한다(leaver, "KR-37050", mapId);
            칠한다(owner, "KR-37011", null);
            세션으로(owner, put("/maps/" + mapId + "/visits/KR-37020/" + guest.explorerId() + "/dispute")
                .contentType(MediaType.APPLICATION_JSON).content("{\"disputed\":true}")).andExpect(status().isOk());
            세션으로(leaver, post("/maps/" + mapId + "/leave")).andExpect(status().isOk());
            정리될_때까지();
            return new League(owner, guest, leaver, mapId);
        }

        private JsonNode 지도_랭킹(League l) throws Exception {
            return json(세션으로(l.guest(), get("/rankings/maps/" + l.mapId())).andExpect(status().isOk()));
        }

        @Nested
        @DisplayName("지도 안 랭킹")
        class MapLeaderboard {

            @Test
            @DisplayName("지금 멤버만 영토 수 순으로 영토·선점·전설을 세고 내 줄을 표시한다")
            void currentMembersOnly() throws Exception {
                League l = 경쟁한_지도();
                JsonNode ranking = 지도_랭킹(l);
                assertThat(ranking.get("rows")).hasSize(2);
                JsonNode top = ranking.get("rows").get(0);
                assertThat(top.get("handle").asText()).isEqualTo(l.owner().handle());
                assertThat(top.get("territories").asInt()).isEqualTo(3);
                assertThat(top.get("legends").asInt()).isEqualTo(1);
                JsonNode second = ranking.get("rows").get(1);
                assertThat(second.get("me").asBoolean()).isTrue();
                assertThat(second.get("territories").asInt()).isEqualTo(1);
                assertThat(second.get("claims").asInt()).isZero();
                assertThat(second.get("rank").asInt()).isEqualTo(2);
            }

            @Test
            @DisplayName("이의 표시된 방문은 빼고, 이의 걸린 선점 대신 그 다음 방문을 선점으로 센다")
            void disputedExcluded() throws Exception {
                JsonNode ranking = 지도_랭킹(경쟁한_지도());
                assertThat(ranking.get("disputedExcluded").asInt()).isEqualTo(1);
                assertThat(ranking.get("rows").get(0).get("claims").asInt()).isEqualTo(3);
            }

            @Test
            @DisplayName("떠난 멤버는 그 지도의 랭킹을 볼 수 없다")
            void leaverCannotView() throws Exception {
                League l = 경쟁한_지도();
                세션으로(l.leaver(), get("/rankings/maps/" + l.mapId())).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
            }
        }

        @Nested
        @DisplayName("친구 랭킹")
        class FriendLeaderboard {

            private League 맞팔한_경쟁자() throws Exception {
                League l = 경쟁한_지도();
                공개_범위(l.owner(), "FRIENDS");
                숨은_대상_팔로우(l.guest(), l.owner());
                팔로우(l.owner(), l.guest());
                return l;
            }

            @Test
            @DisplayName("탐험가 단위로 지역을 중복 없이 세고, 지도 안 이의 표시는 영향을 주지 않는다")
            void distinctRegionsPerExplorer() throws Exception {
                League l = 맞팔한_경쟁자();
                JsonNode friends = json(세션으로(l.guest(), get("/rankings/friends")).andExpect(status().isOk()));
                assertThat(friends.get("friendCount").asInt()).isEqualTo(1);
                assertThat(friends.get("baseline").isNull()).isTrue();
                JsonNode ownerRow = friends.get("rows").get(0);
                JsonNode myRow = friends.get("rows").get(1);
                assertThat(ownerRow.get("handle").asText()).isEqualTo(l.owner().handle());
                assertThat(ownerRow.get("regionCount").asInt()).as("공유·개인 지도의 KR-37011 은 1곳").isEqualTo(3);
                assertThat(ownerRow.get("rank").asInt()).isEqualTo(1);
                assertThat(myRow.get("me").asBoolean()).isTrue();
                assertThat(myRow.get("regionCount").asInt()).as("이의 표시는 탐험가 단위 집계에 영향 없음").isEqualTo(2);
            }

            @Test
            @DisplayName("비공개로 돌린 맞팔 친구는 친구 랭킹과 비교에서 빠진다")
            void privateFriendHidden() throws Exception {
                League l = 맞팔한_경쟁자();
                공개_범위(l.owner(), "PRIVATE");
                JsonNode hiddenFriend = json(세션으로(l.guest(), get("/rankings/friends")).andExpect(status().isOk()));
                assertThat(hiddenFriend.get("friendCount").asInt()).isZero();
                assertThat(hiddenFriend.get("rows")).hasSize(1);
                assertThat(hiddenFriend.get("rows").get(0).get("me").asBoolean()).isTrue();
                세션으로(l.guest(), get("/compare/" + l.owner().handle())).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
            }
        }

        @Nested
        @DisplayName("상위 퍼센트와 지역 통계")
        class Percentile {

            record Population(Session busy, Session lazy, Session none, JsonNode batch) {}

            /** 경남 세 곳·한 곳·0곳을 칠한 세 사람 + 하루 한 번 도는 집계. */
            private Population 집계한_세_사람() throws Exception {
                Session busy = 로그인();
                Session lazy = 로그인();
                Session none = 로그인();
                for (String code : List.of("KR-38050", "KR-38060", "KR-38030")) 칠한다(busy, code, null);
                칠한다(lazy, "KR-38050", null);
                정리될_때까지();
                JsonNode batch = json(mvc.perform(post("/dev/batch/rank")).andExpect(status().isOk()));
                return new Population(busy, lazy, none, batch);
            }

            @Test
            @DisplayName("집계 전에는 아직 계산되지 않았다고 알린다")
            void notComputedBeforeBatch() throws Exception {
                Session busy = 로그인();
                칠한다(busy, "KR-38050", null);
                정리될_때까지();
                세션으로(busy, get("/rankings/me/percentile")).andExpect(jsonPath("$.computed").value(false));
            }

            @Test
            @DisplayName("모집단은 칠한 곳이 한 곳 이상인 활성 탐험가이고, 많이 칠할수록 상위다")
            void populationAndOrder() throws Exception {
                Population p = 집계한_세_사람();
                int withRegions = jdbc.queryForObject("SELECT COUNT(DISTINCT r.explorer_id) FROM explorer_region r JOIN explorer e "
                    + "ON e.id = r.explorer_id WHERE r.active_map_count > 0 AND e.status = 'ACTIVE'", Integer.class);
                assertThat(p.batch().get("population").asInt()).isEqualTo(withRegions);
                JsonNode high = json(세션으로(p.busy(), get("/rankings/me/percentile")).andExpect(status().isOk()));
                JsonNode low = json(세션으로(p.lazy(), get("/rankings/me/percentile")).andExpect(status().isOk()));
                assertThat(high.get("computed").asBoolean()).isTrue();
                assertThat(high.get("regionCount").asInt()).isEqualTo(3);
                assertThat(high.get("rank").asInt()).isLessThan(low.get("rank").asInt());
                assertThat(high.get("topPercent").asInt()).isLessThanOrEqualTo(low.get("topPercent").asInt()).isBetween(1, 100);
                assertThat(high.get("population").asInt()).isEqualTo(p.batch().get("population").asInt());
            }

            @Test
            @DisplayName("칠한 곳이 없으면 순위가 계산되지 않는다")
            void noRegionsNoRank() throws Exception {
                Population p = 집계한_세_사람();
                세션으로(p.none(), get("/rankings/me/percentile")).andExpect(jsonPath("$.computed").value(false));
            }

            @Test
            @DisplayName("지역 통계는 같은 모집단으로 지역별 방문자를 센다")
            void regionStats() throws Exception {
                Population p = 집계한_세_사람();
                JsonNode stats = json(mvc.perform(get("/catalog/region-stats")).andExpect(status().isOk()));
                assertThat(stats.get("population").asInt()).isEqualTo(p.batch().get("population").asInt());
                JsonNode gyeongnam = null;
                for (JsonNode region : stats.get("regions")) if ("KR-38050".equals(region.get("regionCode").asText())) gyeongnam = region;
                assertThat(gyeongnam).isNotNull();
                assertThat(gyeongnam.get("visitorCount").asInt()).isGreaterThanOrEqualTo(2);
            }

            @Test
            @DisplayName("친구가 없으면 주로 다니는 시·도의 평균 탐험가와 비교한다")
            void coldStartUsesMainProvince() throws Exception {
                Population p = 집계한_세_사람();
                JsonNode cold = json(세션으로(p.busy(), get("/rankings/friends")).andExpect(status().isOk()));
                assertThat(cold.get("friendCount").asInt()).isZero();
                assertThat(cold.get("mainProvince").asText()).isEqualTo("KR-38");
                assertThat(cold.get("baseline").get("provinceCode").asText()).isEqualTo("KR-38");
                assertThat(cold.get("baseline").get("explorerCount").asInt()).isGreaterThanOrEqualTo(2);
                assertThat(cold.get("rows")).hasSize(1);
            }

            @Test
            @DisplayName("칠한 곳도 없으면 전국 평균과 비교한다")
            void coldStartNationwide() throws Exception {
                Population p = 집계한_세_사람();
                JsonNode nationwide = json(세션으로(p.none(), get("/rankings/friends")).andExpect(status().isOk()));
                assertThat(nationwide.get("baseline").get("nationwide").asBoolean()).isTrue();
            }

            @Test
            @DisplayName("익명도 내 줄과 평균 비교는 본다")
            void anonymousSeesOwnRow() throws Exception {
                집계한_세_사람();
                mvc.perform(get("/rankings/friends").header(H, 기기().token())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.loggedIn").value(false)).andExpect(jsonPath("$.rows[0].me").value(true));
            }

            @Test
            @DisplayName("지역별 방문자 수는 계정으로 합쳐져 닫힌 익명 탐험가를 세지 않는다")
            void mergedExplorerNotCounted() throws Exception {
                String email = 새_이메일("stats");
                Session account = 로그인(null, email);
                칠한다(account, "KR-35011", null);
                Anonymous device = 기기();
                칠한다(device, "KR-35011");
                칠한다(device, "KR-35012");
                정리될_때까지();
                로그인(device, email);
                정리될_때까지();

                mvc.perform(post("/dev/batch/rank")).andExpect(status().isOk());
                JsonNode stats = json(mvc.perform(get("/catalog/region-stats")).andExpect(status().isOk()));
                for (JsonNode region : stats.get("regions")) {
                    String code = region.get("regionCode").asText();
                    if (List.of("KR-35011", "KR-35012").contains(code)) {
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
        }
    }
}
