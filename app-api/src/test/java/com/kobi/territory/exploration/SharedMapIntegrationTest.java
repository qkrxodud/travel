package com.kobi.territory.exploration;

import static com.kobi.territory.support.Explorers.방문;
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
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.exploration.application.MapPurgeJob;
import com.kobi.territory.outbox.OutboxRedelivery;
import com.kobi.territory.progression.api.event.SetCompleted;
import com.kobi.territory.progression.application.RecalculateService;
import com.kobi.territory.support.Explorers;
import com.kobi.territory.support.Explorers.Anonymous;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.IntegrationTestConfig.FaultInjection;
import com.kobi.territory.support.MutableClock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
 * 3단계 공유 지도 D2·D3: 토큰 인증 → 지도 만들기·초대코드 합류 → 각자 체크인·선점 → 지도장 설정·이의 → 테마 완성 멤버 전원 보상 →
 * 탈퇴(숨김·선점 이전 +10) → 재가입 복구(선점은 안 돌아옴) → 유예 종료 하드 삭제. 회귀 출처: R2-1(재계산 수령자), S3-1(진행 루트 선생성),
 * S3-3(재계산 보류), N2(비멤버 잠금 전 거절), Q1(하루 상한 낮추기만).
 */
@IntegrationTest
@DisplayName("공유 지도에서 함께 칠하기")
class SharedMapIntegrationTest {

    static final List<String> JIRI = List.of("KR-35050", "KR-36330", "KR-38360", "KR-38370", "KR-38380");
    static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired MapPurgeJob purgeJob;
    @Autowired RecalculateService recalculate;
    @Autowired OutboxRedelivery redelivery;
    @Autowired Explorers explorers;

    private Anonymous 탐험가() throws Exception {
        return explorers.익명_탐험가();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return explorers.json(result);
    }

    private ResultActions call(Anonymous who, MockHttpServletRequestBuilder builder, Object body) throws Exception {
        builder.header(Explorers.TOKEN, who.token());
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        return mvc.perform(builder);
    }

    private ResultActions 칠한다(Anonymous who, String mapId, String code) throws Exception {
        return explorers.체크인(who, 방문(code, LocalDate.now(clock), null, mapId));
    }

    private static void error(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    private long xp(Anonymous who) {
        Long xp = jdbc.query("SELECT xp FROM explorer_progress WHERE explorer_id = ?",
            resultSet -> resultSet.next() ? resultSet.getLong(1) : null, who.id());
        return xp == null ? 0 : xp;
    }

    private boolean 장부에_있다(Anonymous who, String refId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id = ?", Integer.class,
            who.id(), refId) == 1;
    }

    private int 활성_지역_수(Anonymous who) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM explorer_region WHERE explorer_id = ? AND active_map_count > 0",
            Integer.class, who.id());
    }

    private String 지도를_만든다(Anonymous owner, String name) throws Exception {
        return explorers.공유_지도를_만든다(owner, name).get("mapId").asText();
    }

    private String 초대코드(Anonymous member, String mapId) throws Exception {
        return explorers.초대코드(member, mapId);
    }

    private void 합류한다(Anonymous who, Anonymous owner, String mapId) throws Exception {
        clock.advance(Duration.ofSeconds(1)); // 가입 순서(멤버 목록 순서)가 분명하게
        call(who, post("/maps/join"), Map.of("inviteCode", 초대코드(owner, mapId))).andExpect(status().isOk());
    }

    private String 개인_지도(Anonymous who) throws Exception {
        return json(call(who, get("/explorers/me"), null)).get("personalMapId").asText();
    }

    /** 지도장과 멤버 한 명이 있는 공유 지도. */
    record Expedition(Anonymous owner, Anonymous friend, String mapId) {}

    private Expedition 둘이_함께하는_지도() throws Exception {
        Anonymous owner = 탐험가();
        Anonymous friend = 탐험가();
        String mapId = 지도를_만든다(owner, "부산 원정대");
        합류한다(friend, owner, mapId);
        return new Expedition(owner, friend, mapId);
    }

    @Nested
    @DisplayName("지도를 만들고 합류할 때")
    class CreateAndJoin {

        @Test
        @DisplayName("만든 지도는 공유 지도이고 세 가지 규칙과 지도장이 정해진다")
        void createdMapIsShared() throws Exception {
            call(탐험가(), post("/maps"), Map.of("name", "부산 원정대")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("SHARED"))
                .andExpect(jsonPath("$.rules.length()").value(3))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
        }

        @Test
        @DisplayName("멤버가 아니면 그 지도에 칠할 수 없다")
        void nonMemberCannotCheckIn() throws Exception {
            Anonymous owner = 탐험가();
            String mapId = 지도를_만든다(owner, "부산 원정대");
            error(칠한다(탐험가(), mapId, "KR-26010"), 403, "NOT_A_MEMBER");
        }

        @Test
        @DisplayName("없는 초대코드로는 합류할 수 없다")
        void unknownInviteCode() throws Exception {
            error(call(탐험가(), post("/maps/join"), Map.of("inviteCode", "ZZZZZZZZ")), 404, "INVITE_CODE_NOT_FOUND");
        }

        @Test
        @DisplayName("초대코드는 대소문자를 가리지 않고, 합류하면 멤버가 둘이 된다")
        void joinWithLowerCaseCode() throws Exception {
            Anonymous owner = 탐험가();
            String mapId = 지도를_만든다(owner, "부산 원정대");
            call(탐험가(), post("/maps/join"), Map.of("inviteCode", 초대코드(owner, mapId).toLowerCase())).andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.rejoined").value(false));
        }

        @Test
        @DisplayName("이미 멤버면 다시 합류할 수 없다")
        void alreadyMember() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            error(call(map.friend(), post("/maps/join"), Map.of("inviteCode", 초대코드(map.owner(), map.mapId()))), 409,
                "ALREADY_MEMBER");
        }

        @Test
        @DisplayName("합류한 지도는 내 개인 지도 다음에 지도 목록에 보인다")
        void joinedMapIsListed() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            call(map.friend(), get("/maps"), null).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].kind").value("PERSONAL")).andExpect(jsonPath("$[1].mapId").value(map.mapId()));
        }

        @Test
        @DisplayName("멤버가 아니면 지도를 들여다볼 수 없다")
        void strangerCannotView() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            error(call(탐험가(), get("/maps/" + map.mapId()), null), 403, "NOT_A_MEMBER");
        }
    }

    @Nested
    @DisplayName("같은 지도에서 각자 칠할 때")
    class Competing {

        private Expedition 셋이_칠한_지도() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            칠한다(map.owner(), map.mapId(), "KR-26010").andExpect(status().isCreated()).andExpect(jsonPath("$.firstClaim").value(true));
            칠한다(map.friend(), map.mapId(), "KR-26010").andExpect(status().isCreated()).andExpect(jsonPath("$.firstClaim").value(false));
            칠한다(map.friend(), map.mapId(), "KR-26020").andExpect(status().isCreated());
            return map;
        }

        @Test
        @DisplayName("같은 지역도 각자 칠할 수 있고 지역 색은 먼저 칠한 사람의 것이다")
        void firstPainterClaims() throws Exception {
            Expedition map = 셋이_칠한_지도();
            JsonNode detail = json(call(map.friend(), get("/maps/" + map.mapId()), null));
            assertThat(detail.get("claims").toString()).contains("KR-26010\",\"explorerId\":\"" + map.owner().id());
        }

        @Test
        @DisplayName("멤버마다 색이 다르고 각자 칠한 곳 수가 보인다")
        void membersHaveColorsAndCounts() throws Exception {
            Expedition map = 셋이_칠한_지도();
            JsonNode detail = json(call(map.friend(), get("/maps/" + map.mapId()), null));
            assertThat(detail.get("members").get(0).get("color").asText())
                .isNotEqualTo(detail.get("members").get(1).get("color").asText());
            assertThat(detail.get("members").get(1).get("regionCount").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("지도의 영토에는 모든 멤버의 방문과 지역별 선점이 보인다")
        void territoryShowsAllMembers() throws Exception {
            Expedition map = 셋이_칠한_지도();
            call(map.owner(), get("/territory").param("mapId", map.mapId()), null).andExpect(jsonPath("$.visits.length()").value(3))
                .andExpect(jsonPath("$.claims.length()").value(2));
        }
    }

    @Nested
    @DisplayName("지도장이 지도 설정을 바꿀 때")
    class Settings {

        private void 설정(Anonymous who, String mapId, boolean photo, int cap, int status, String code) throws Exception {
            ResultActions result = call(who, put("/maps/" + mapId + "/settings"), Map.of("photoRequired", photo, "dailyCheckInCap", cap));
            if (code == null) result.andExpect(status().is(status));
            else error(result, status, code);
        }

        @Test
        @DisplayName("지도장이 아니면 설정을 바꿀 수 없다")
        void ownerOnly() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            설정(map.friend(), map.mapId(), true, 1, 403, "OWNER_ONLY");
        }

        @Test
        @DisplayName("사진 필수로 바꾸면 사진 없는 체크인은 거절된다")
        void photoRequired() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            call(map.owner(), put("/maps/" + map.mapId() + "/settings"), Map.of("photoRequired", true, "dailyCheckInCap", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.settings.photoRequired").value(true));
            error(칠한다(map.friend(), map.mapId(), "KR-26030"), 422, "PHOTO_REQUIRED");
        }

        @Test
        @DisplayName("지도장이 낮춘 하루 상한이 온보딩이 끝난 멤버에게 적용된다")
        void loweredCapApplies() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            칠한다(map.friend(), map.mapId(), "KR-26010").andExpect(status().isCreated());
            칠한다(map.friend(), map.mapId(), "KR-26020").andExpect(status().isCreated());
            설정(map.owner(), map.mapId(), true, 1, 200, null);
            call(map.friend(), post("/dev/explorers/age"), Map.of("hours", 73)).andExpect(status().isOk());
            error(call(map.friend(), post("/visits"), Map.of("regionCode", "KR-26030", "visitDate", LocalDate.now(clock).toString(),
                "mapId", map.mapId(), "photoUrl", "https://example.com/a.jpg")), 422, "DAILY_CAP_EXCEEDED");
        }

        @Test
        @DisplayName("하루 상한은 기본값보다 높일 수 없다")
        void capCannotExceedDefault() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            설정(map.owner(), map.mapId(), false, 5, 200, null);
            설정(map.owner(), map.mapId(), false, 6, 400, "INVALID_SETTINGS");
        }

        @Test
        @DisplayName("개인 지도는 설정을 바꿀 수 없다")
        void personalMapHasNoSettings() throws Exception {
            Anonymous owner = 탐험가();
            설정(owner, 개인_지도(owner), false, 3, 422, "PERSONAL_MAP_ONLY_ME");
        }
    }

    @Nested
    @DisplayName("지도장이 방문에 이의를 걸 때")
    class Dispute {

        private String 이의_경로(Expedition map) throws Exception {
            칠한다(map.friend(), map.mapId(), "KR-26020").andExpect(status().isCreated());
            return "/maps/" + map.mapId() + "/visits/KR-26020/" + map.friend().id() + "/dispute";
        }

        @Test
        @DisplayName("지도장이 아니면 이의를 걸 수 없다")
        void ownerOnly() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            error(call(map.friend(), put(이의_경로(map)), Map.of("disputed", true)), 403, "OWNER_ONLY");
        }

        @Test
        @DisplayName("이의를 건 방문은 지도에 이의 표시로 보인다")
        void disputedVisitIsShown() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            call(map.owner(), put(이의_경로(map)), Map.of("disputed", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.disputed").value(true));
            call(map.owner(), get("/maps/" + map.mapId()), null).andExpect(jsonPath("$.disputed[0].regionCode").value("KR-26020"));
        }
    }

    @Nested
    @DisplayName("초대코드를 다시 만들 때")
    class RegenerateInvite {

        @Test
        @DisplayName("지도장만 다시 만들 수 있다")
        void ownerOnly() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            error(call(map.friend(), post("/maps/" + map.mapId() + "/invite-code"), null), 403, "OWNER_ONLY");
        }

        @Test
        @DisplayName("새 코드가 생기고 예전 코드로는 더 이상 합류할 수 없다")
        void oldCodeStopsWorking() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            String code = 초대코드(map.owner(), map.mapId());
            String newCode = json(call(map.owner(), post("/maps/" + map.mapId() + "/invite-code"), null)).get("inviteCode").asText();
            assertThat(newCode).isNotEqualTo(code);
            error(call(탐험가(), post("/maps/join"), Map.of("inviteCode", code)), 404, "INVITE_CODE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("지도장 넘기기")
    class TransferOwner {

        @Test
        @DisplayName("지도장은 넘기기 전에는 지도를 떠날 수 없다")
        void ownerCannotLeave() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            error(call(map.owner(), post("/maps/" + map.mapId() + "/leave"), null), 422, "OWNER_CANNOT_LEAVE");
        }

        @Test
        @DisplayName("넘기면 그 멤버가 새 지도장이 된다")
        void transferMakesNewOwner() throws Exception {
            Expedition map = 둘이_함께하는_지도();
            call(map.owner(), post("/maps/" + map.mapId() + "/transfer-owner"), Map.of("explorerId", map.friend().id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(map.friend().id()));
        }

        @Test
        @DisplayName("개인 지도는 떠날 수 없다")
        void cannotLeavePersonalMap() throws Exception {
            Anonymous owner = 탐험가();
            error(call(owner, post("/maps/" + 개인_지도(owner) + "/leave"), null), 422, "PERSONAL_MAP_ONLY_ME");
        }
    }

    @Nested
    @DisplayName("함께 테마를 완성하면")
    class ThemeCompletion {

        @Test
        @DisplayName("완성 순간의 멤버는 한 곳도 안 칠했어도 테마 보너스와 칭호를 받는다")
        void allMembersAtCompletionAreRewarded() throws Exception {
            Anonymous owner = 탐험가();
            Anonymous friend = 탐험가();
            String mapId = 지도를_만든다(owner, "지리산 원정");
            합류한다(friend, owner, mapId);
            for (String code : JIRI) 칠한다(owner, mapId, code).andExpect(status().isCreated());
            explorers.전달이_끝날_때까지(mapId, owner.id(), friend.id());
            await().atMost(WAIT).until(() -> 장부에_있다(friend, "set:" + friend.id() + ":jiri"));
            assertThat(장부에_있다(owner, "set:" + owner.id() + ":jiri")).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM title_earned WHERE explorer_id = ? AND title_id = 'set-jiri'",
                Integer.class, friend.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT completed_member_ids FROM set_progress WHERE map_id = ? AND set_id = 'jiri'",
                String.class, mapId)).contains(owner.id(), friend.id());
        }

        @Test
        @DisplayName("완성 뒤에 합류한 멤버는 재계산해도 보너스를 받지 않고, 완성 멤버의 보너스는 재계산해도 남는다")
        void lateMemberGetsNoBonusEvenAfterRecalculation() throws Exception {
            Anonymous owner = 탐험가();
            Anonymous friend = 탐험가();
            Anonymous late = 탐험가();
            String mapId = 지도를_만든다(owner, "지리산 원정");
            합류한다(friend, owner, mapId);
            for (String code : JIRI) 칠한다(owner, mapId, code).andExpect(status().isCreated());
            explorers.전달이_끝날_때까지(mapId, owner.id(), friend.id());
            await().atMost(WAIT).until(() -> 장부에_있다(friend, "set:" + friend.id() + ":jiri"));

            합류한다(late, owner, mapId);
            explorers.전달이_끝날_때까지(mapId, late.id());
            recalculate.recalculate(ExplorerId.of(late.id()));
            recalculate.recalculate(ExplorerId.of(friend.id()));
            assertThat(장부에_있다(late, "set:" + late.id() + ":jiri")).isFalse();
            assertThat(장부에_있다(friend, "set:" + friend.id() + ":jiri")).isTrue();
            call(late, get("/collection").param("mapId", mapId), null).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("멤버가 지도를 떠나면")
    class Leaving {

        /** 떠날 멤버가 KR-32010 을 선점(지도장도 칠함)하고 KR-32020 을 혼자 칠한 지도. */
        record Departure(Anonymous owner, Anonymous leaver, String mapId, long ownerXp, long leaverXp) {}

        private Departure 떠날_멤버가_있는_지도() throws Exception {
            Anonymous owner = 탐험가();
            Anonymous leaver = 탐험가();
            String mapId = 지도를_만든다(owner, "강원 원정");
            합류한다(leaver, owner, mapId);
            칠한다(leaver, mapId, "KR-32010").andExpect(status().isCreated());
            칠한다(owner, mapId, "KR-32010").andExpect(status().isCreated());
            칠한다(leaver, mapId, "KR-32020").andExpect(status().isCreated());
            explorers.전달이_끝날_때까지(mapId, owner.id(), leaver.id());
            return new Departure(owner, leaver, mapId, xp(owner), xp(leaver));
        }

        private JsonNode 떠난다(Departure map) throws Exception {
            JsonNode left = json(call(map.leaver(), post("/maps/" + map.mapId() + "/leave"), null).andExpect(status().isOk()));
            explorers.전달이_끝날_때까지(map.mapId(), map.owner().id(), map.leaver().id());
            return left;
        }

        @Test
        @DisplayName("떠난 멤버의 방문은 숨겨지고 몇 곳이 숨겨지는지 안내받으며, 그 지도에 더는 칠할 수 없다")
        void visitsAreHidden() throws Exception {
            Departure map = 떠날_멤버가_있는_지도();
            JsonNode left = 떠난다(map);
            assertThat(left.get("hiddenRegionCount").asInt()).isEqualTo(2);
            assertThat(left.get("message").asText()).contains("2곳");
            error(칠한다(map.leaver(), map.mapId(), "KR-32030"), 403, "NOT_A_MEMBER");
            call(map.owner(), get("/territory").param("mapId", map.mapId()), null).andExpect(jsonPath("$.visits.length()").value(1));
            call(map.owner(), get("/maps/" + map.mapId()), null).andExpect(jsonPath("$.departing").value(1));
        }

        @Test
        @DisplayName("떠난 멤버의 선점은 다음에 칠한 멤버에게 넘어가고 새 선점자가 선점 보너스를 받는다")
        void claimTransfersWithBonus() throws Exception {
            Departure map = 떠날_멤버가_있는_지도();
            떠난다(map);
            call(map.owner(), get("/maps/" + map.mapId()), null).andExpect(jsonPath("$.claims[0].explorerId").value(map.owner().id()));
            await().atMost(WAIT).until(() -> xp(map.owner()) == map.ownerXp() + 10);
            assertThat(장부에_있다(map.owner(), "claim:" + map.mapId() + ":KR-32010:" + map.owner().id())).isTrue();
        }

        @Test
        @DisplayName("떠난 사람의 경험치와 탐험가 단위로 칠한 곳 수는 줄지 않는다")
        void leaverKeepsProgress() throws Exception {
            Departure map = 떠날_멤버가_있는_지도();
            떠난다(map);
            assertThat(xp(map.leaver())).isEqualTo(map.leaverXp());
            assertThat(활성_지역_수(map.leaver())).isEqualTo(2);
        }

        @Test
        @DisplayName("유예 안에 다시 합류하면 방문은 돌아오지만 넘어간 선점은 돌아오지 않는다")
        void rejoinRestoresVisitsButNotClaims() throws Exception {
            Departure map = 떠날_멤버가_있는_지도();
            떠난다(map);
            clock.advance(Duration.ofMinutes(1));
            call(map.leaver(), post("/maps/join"), Map.of("inviteCode", 초대코드(map.owner(), map.mapId()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.rejoined").value(true));
            explorers.전달이_끝날_때까지(map.mapId());
            call(map.leaver(), get("/territory").param("mapId", map.mapId()), null).andExpect(jsonPath("$.visits.length()").value(3));
            JsonNode claims = json(call(map.leaver(), get("/maps/" + map.mapId()), null).andExpect(status().isOk())).get("claims");
            assertThat(claims.toString()).contains("KR-32010\",\"explorerId\":\"" + map.owner().id())
                .contains("KR-32020\",\"explorerId\":\"" + map.leaver().id());
        }

        @Test
        @DisplayName("유예 7일이 지나면 방문과 멤버 기록이 지워지지만 탐험가 단위 기록과 경험치는 재계산해도 남는다")
        void purgeAfterGrace() throws Exception {
            Departure map = 떠날_멤버가_있는_지도();
            떠난다(map);
            purgeJob.run();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM map_member WHERE map_id = ? AND explorer_id = ? AND left_at IS NOT NULL",
                Integer.class, map.mapId(), map.leaver().id())).as("유예 중에는 지우지 않는다").isEqualTo(1);
            clock.advance(Duration.ofDays(8));
            assertThat(purgeJob.run()).isGreaterThanOrEqualTo(1);
            explorers.전달이_끝날_때까지(map.mapId());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ? AND checked_in_by = ?", Integer.class,
                map.mapId(), map.leaver().id())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM map_member WHERE map_id = ? AND explorer_id = ?", Integer.class,
                map.mapId(), map.leaver().id())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM map_member WHERE map_id = ? AND explorer_id = ? AND left_at IS NULL",
                Integer.class, map.mapId(), map.owner().id())).as("남은 지도장은 그대로다").isEqualTo(1);
            call(map.owner(), get("/maps/" + map.mapId()), null)
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.departing").value(0));
            call(map.owner(), get("/territory").param("mapId", map.mapId()), null).andExpect(jsonPath("$.visits.length()").value(1));
            assertThat(활성_지역_수(map.leaver())).isEqualTo(2);
            recalculate.recalculate(ExplorerId.of(map.leaver().id()));
            assertThat(xp(map.leaver())).isEqualTo(map.leaverXp());
        }

        @Test
        @DisplayName("다시 합류했다가 다시 떠난 멤버도 유예가 끝나면 지워진다")
        void rejoinedThenLeftIsPurged() throws Exception {
            Departure map = 떠날_멤버가_있는_지도();
            떠난다(map);
            clock.advance(Duration.ofMinutes(1));
            call(map.leaver(), post("/maps/join"), Map.of("inviteCode", 초대코드(map.owner(), map.mapId()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.rejoined").value(true));
            explorers.전달이_끝날_때까지(map.mapId());
            떠난다(map);
            clock.advance(Duration.ofDays(8));
            assertThat(purgeJob.run()).isGreaterThanOrEqualTo(1);
            explorers.전달이_끝날_때까지(map.mapId());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ? AND checked_in_by = ?", Integer.class,
                map.mapId(), map.leaver().id())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM map_member WHERE map_id = ? AND explorer_id = ?", Integer.class,
                map.mapId(), map.leaver().id())).isZero();
            call(map.owner(), get("/maps/" + map.mapId()), null)
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.departing").value(0));
            assertThat(xp(map.leaver())).isEqualTo(map.leaverXp());
        }
    }

    @Nested
    @DisplayName("취소하고 다시 칠하면")
    class Generations {

        private Anonymous 세_번_칠한_탐험가() throws Exception {
            Anonymous me = 탐험가();
            칠한다(me, null, "KR-11010").andExpect(status().isCreated()).andExpect(jsonPath("$.visit.generation").value(1));
            call(me, delete("/visits/KR-11010"), null).andExpect(status().isNoContent());
            칠한다(me, null, "KR-11010").andExpect(status().isCreated()).andExpect(jsonPath("$.visit.generation").value(2));
            call(me, delete("/visits/KR-11010"), null).andExpect(status().isNoContent());
            칠한다(me, null, "KR-11010").andExpect(status().isCreated()).andExpect(jsonPath("$.visit.generation").value(3));
            return me;
        }

        private int 회차(String payload) {
            try {
                return om.readTree(payload).get("visitGeneration").asInt();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Test
        @DisplayName("방문 기록이 지워져도 회차는 1, 2, 3으로 계속 오른다")
        void generationKeepsRising() throws Exception {
            세_번_칠한_탐험가();
        }

        @Test
        @DisplayName("칠함·취소 소식에 그 회차가 실리고, 칠함 소식에는 그때의 멤버가 실린다")
        void newsCarriesGeneration() throws Exception {
            Anonymous me = 세_번_칠한_탐험가();
            List<String> visited = jdbc.queryForList("SELECT payload FROM outbox WHERE event_type LIKE '%RegionVisited' "
                + "AND payload LIKE ? ORDER BY id", String.class, "%" + me.id() + "%");
            assertThat(visited).hasSize(3);
            assertThat(회차(visited.get(1))).isEqualTo(2);
            assertThat(om.readTree(visited.get(1)).get("memberIds").get(0).asText()).isEqualTo(me.id());
            List<Integer> generations = jdbc.queryForList("SELECT payload FROM outbox WHERE payload LIKE ? AND "
                + "(event_type LIKE '%RegionVisited' OR event_type LIKE '%VisitCancelled') ORDER BY id", String.class, "%" + me.id() + "%")
                .stream().map(this::회차).toList();
            assertThat(generations).containsExactly(1, 1, 2, 2, 3);
        }

        @Test
        @DisplayName("진행은 마지막 회차만 칠한 것으로 보아 기본 경험치가 한 번만 살아 있다")
        void progressionUsesLatestGeneration() throws Exception {
            Anonymous me = 세_번_칠한_탐험가();
            await().atMost(WAIT).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? "
                + "AND ref_id LIKE 'region:%'", Integer.class, me.id()) == 5);
            assertThat(장부에_있다(me, "region:" + me.id() + ":KR-11010#3")).isTrue();
            assertThat(jdbc.queryForObject("SELECT active_map_count FROM explorer_region WHERE explorer_id = ? AND region_code = 'KR-11010'",
                Integer.class, me.id())).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("진행 다시 계산")
    class Recalculation {

        @Test
        @DisplayName("가입하면 진행 기록이 경험치 0으로 미리 만들어진다")
        void progressRowCreatedOnRegistration() throws Exception {
            Anonymous me = 탐험가();
            await().atMost(WAIT).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM explorer_progress WHERE explorer_id = ?",
                Integer.class, me.id()) == 1);
            assertThat(xp(me)).isZero();
        }

        @Test
        @DisplayName("아직 전달되지 못한 소식이 남은 탐험가는 다시 계산을 미루고, 다시 보내 전달되면 계산한다")
        void deferredUntilDelivered() throws Exception {
            Anonymous me = 탐험가();
            String personal = 개인_지도(me);
            explorers.전달이_끝날_때까지(personal, me.id());
            FaultInjection.failNext("progression.progress", RegionVisited.class, me.id(), 5);
            try {
                칠한다(me, null, "KR-11010").andExpect(status().isCreated());
                await().atMost(WAIT).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery d JOIN outbox o "
                    + "ON o.id = d.event_id WHERE o.aggregate_id = ? AND d.status = 'FAILED'", Integer.class, personal) == 1);
                assertThat(recalculate.recalculateIfSettled(ExplorerId.of(me.id()))).isFalse();
                assertThat(recalculate.recalculateAll().deferredExplorerIds()).contains(me.id());
            } finally {
                FaultInjection.clear();
            }
            redelivery.redeliverFailed(null, "progression.progress");
            explorers.전달이_끝날_때까지(personal, me.id());
            assertThat(recalculate.recalculateIfSettled(ExplorerId.of(me.id()))).isTrue();
            assertThat(xp(me)).isEqualTo(35);
        }
    }

    @Nested
    @DisplayName("예전 형식으로 쌓인 소식")
    class LegacyNews {

        @Test
        @DisplayName("칠함 소식은 회차 0, 멤버 목록 없음으로 읽힌다")
        void legacyRegionVisited() throws Exception {
            String visited = "{\"explorerId\":\"11111111-1111-1111-1111-111111111111\",\"mapId\":\"33333333-3333-3333-3333-333333333333\","
                + "\"regionCode\":\"KR-11010\",\"rarity\":\"COMMON\",\"provinceCode\":\"KR-11\",\"visitedAt\":\"2026-10-02T03:00:00Z\","
                + "\"visitDate\":\"2026-10-02\",\"isFirstInProvince\":true,\"nth\":1,\"isFirstClaim\":true}";
            var regionVisited = om.readValue(visited, RegionVisited.class);
            assertThat(regionVisited.visitGeneration()).isZero();
            assertThat(regionVisited.memberIds()).isNull();
        }

        @Test
        @DisplayName("취소 소식은 회차 0으로 읽힌다")
        void legacyVisitCancelled() throws Exception {
            String cancelled = "{\"explorerId\":\"11111111-1111-1111-1111-111111111111\",\"mapId\":\"33333333-3333-3333-3333-333333333333\","
                + "\"regionCode\":\"KR-11010\",\"rarity\":\"COMMON\",\"provinceCode\":\"KR-11\",\"wasClaim\":true,\"remaining\":0,"
                + "\"regionStillOnMap\":false,\"cancelledAt\":\"2026-10-02T03:00:01Z\"}";
            assertThat(om.readValue(cancelled, VisitCancelled.class).visitGeneration()).isZero();
        }

        @Test
        @DisplayName("테마 완성 소식은 받는 사람 하나로 읽히고 완성자·수령자 목록은 비어 있다")
        void legacySetCompleted() throws Exception {
            String completed = "{\"mapId\":\"33333333-3333-3333-3333-333333333333\",\"setId\":\"jiri\","
                + "\"explorerId\":\"11111111-1111-1111-1111-111111111111\",\"completedAt\":\"2026-10-02T03:00:02Z\"}";
            var setCompleted = om.readValue(completed, SetCompleted.class);
            assertThat(setCompleted.completedBy()).isNull();
            assertThat(setCompleted.recipientIds()).isNull();
            assertThat(setCompleted.explorerId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        }
    }
}
