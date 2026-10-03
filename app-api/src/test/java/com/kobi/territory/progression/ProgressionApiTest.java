package com.kobi.territory.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 2단계 D3: 진행 API 계약(MockMvc) — 진행·칭호 선택·도감·퀘스트·보상 받기. 회귀 출처 D1(미리보기 = 지도 기준 최대), S3-3(재계산 보류). */
@IntegrationTest
@DisplayName("진행 화면 — 경험치·도감·퀘스트·칭호")
class ProgressionApiTest {

    private static final String H = "X-Explorer-Token";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;

    private String me;
    private String token;
    private String myMap;

    @BeforeEach
    void issue() throws Exception {
        JsonNode body = json(mvc.perform(post("/explorers")).andExpect(status().isCreated()));
        me = body.get("explorerId").asText();
        token = body.get("accessToken").asText();
        myMap = body.get("personalMapId").asText();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return om.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder) {
        return builder.header(H, token);
    }

    private void 칠한다(String code) throws Exception {
        String body = om.writeValueAsString(Map.of("regionCode", code, "visitDate", LocalDate.now(clock).toString()));
        mvc.perform(as(post("/visits")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    private JsonNode progress() throws Exception {
        return json(mvc.perform(as(get("/progress"))).andExpect(status().isOk()));
    }

    private void 경험치가_될_때까지(int xp) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(progress().get("xp").asInt()).isEqualTo(xp));
    }

    /** 지리산 둘레 다섯 곳을 모두 칠하고 경험치가 반영될 때까지 기다린다. */
    private void 지리산_둘레를_칠한다() throws Exception {
        for (String code : ProgressionIntegrationTest.JIRI) 칠한다(code);
        경험치가_될_때까지(ProgressionIntegrationTest.JIRI_XP);
    }

    private ResultActions 칭호를_고른다(String titleJson) throws Exception {
        return mvc.perform(as(put("/progress/title")).contentType(MediaType.APPLICATION_JSON).content(titleJson));
    }

    private static void error(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Nested
    @DisplayName("누가 요청하는지 모르면")
    class Identity {

        @Test
        @DisplayName("토큰 없이는 진행을 볼 수 없다")
        void noToken() throws Exception {
            error(mvc.perform(get("/progress")), 401, "EXPLORER_TOKEN_REQUIRED");
        }

        @Test
        @DisplayName("모르는 토큰이나 공개된 탐험가 식별자로는 진행·퀘스트·도감을 볼 수 없다")
        void unknownToken() throws Exception {
            error(mvc.perform(get("/quests").header(H, "not-a-token")), 401, "EXPLORER_TOKEN_INVALID");
            error(mvc.perform(get("/collection").header(H, "00000000-0000-0000-0000-000000000000")), 401, "EXPLORER_TOKEN_INVALID");
            error(mvc.perform(get("/progress").header(H, me)), 401, "EXPLORER_TOKEN_INVALID");
        }
    }

    @Nested
    @DisplayName("막 시작한 탐험가")
    class Beginner {

        @Test
        @DisplayName("경험치 0·레벨 1·첫 칭호이고, 뱃지 12종과 칭호 35종 목록을 본다")
        void startsAtLevelOne() throws Exception {
            mvc.perform(as(get("/progress"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.xp").value(0))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.levelTitle").value("초보 탐험가"))
                .andExpect(jsonPath("$.currentLevelXp").value(0))
                .andExpect(jsonPath("$.nextLevelXp").value(40))
                .andExpect(jsonPath("$.title.id").value("lv1"))
                .andExpect(jsonPath("$.streak.months").value(0))
                .andExpect(jsonPath("$.badgeCount").value(0))
                .andExpect(jsonPath("$.badges", hasSize(12)))
                .andExpect(jsonPath("$.titles", hasSize(35)))
                .andExpect(jsonPath("$.titles[0].earned").value(true));
        }

        @Test
        @DisplayName("도감에는 지역 목록과 완성 보상이 적힌 테마 9개가 비어 있다")
        void emptyCollection() throws Exception {
            mvc.perform(as(get("/collection"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.mapId").value(myMap))
                .andExpect(jsonPath("$.completed").value(0))
                .andExpect(jsonPath("$.sets", hasSize(9)))
                .andExpect(jsonPath("$.sets[7].id").value("jiri"))
                .andExpect(jsonPath("$.sets[7].total").value(5))
                .andExpect(jsonPath("$.sets[7].rewardXp").value(100))
                .andExpect(jsonPath("$.sets[7].regions[0].name").value("남원시"));
        }

        @Test
        @DisplayName("퀘스트는 이번 달 4개와 상시 도전 3개가 있고 아직 받을 보상이 없다")
        void questsOfThisMonth() throws Exception {
            mvc.perform(as(get("/quests"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(YearMonth.now(clock).toString()))
                .andExpect(jsonPath("$.monthly", hasSize(4)))
                .andExpect(jsonPath("$.always", hasSize(3)))
                .andExpect(jsonPath("$.monthly[0].id").value("m3"))
                .andExpect(jsonPath("$.monthly[0].claimable").value(false));
        }
    }

    @Nested
    @DisplayName("지리산 둘레 다섯 곳을 모두 칠하면")
    class AfterJiri {

        @Test
        @DisplayName("레벨이 오르고 이번 달 연속 탐험이 시작된다")
        void levelAndStreak() throws Exception {
            지리산_둘레를_칠한다();
            mvc.perform(as(get("/progress"))).andExpect(jsonPath("$.level").value(4))
                .andExpect(jsonPath("$.levelTitle").value("동네 산책러"))
                .andExpect(jsonPath("$.streak.months").value(1))
                .andExpect(jsonPath("$.streak.activeThisMonth").value(true));
        }

        @Test
        @DisplayName("도감의 지리산 테마가 다섯 곳 모두 모여 완성된다")
        void themeCompleted() throws Exception {
            지리산_둘레를_칠한다();
            mvc.perform(as(get("/collection"))).andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.sets[7].have").value(5))
                .andExpect(jsonPath("$.sets[7].completed").value(true))
                .andExpect(jsonPath("$.sets[7].completedAt").isNotEmpty());
        }

        @Test
        @DisplayName("이번 달 퀘스트가 모두 달성되어 보상을 받을 수 있다")
        void questsClaimable() throws Exception {
            지리산_둘레를_칠한다();
            JsonNode quests = json(mvc.perform(as(get("/quests"))));
            assertThat(quests.get("monthlyDone").asInt()).isEqualTo(4);
            assertThat(quests.get("monthly").get(0).get("current").asInt()).isEqualTo(3);
            assertThat(quests.get("monthly").get(0).get("claimable").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("퀘스트 보상은 한 번만 받고 그 경험치가 진행에 더해진다")
        void claimOnce() throws Exception {
            지리산_둘레를_칠한다();
            mvc.perform(as(post("/quests/m3/claim"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.xp").value(60)).andExpect(jsonPath("$.period").value(YearMonth.now(clock).toString()));
            error(mvc.perform(as(post("/quests/m3/claim"))), 409, "QUEST_ALREADY_CLAIMED");
            경험치가_될_때까지(ProgressionIntegrationTest.JIRI_XP + 60);
            mvc.perform(as(get("/quests"))).andExpect(jsonPath("$.monthly[0].claimed").value(true))
                .andExpect(jsonPath("$.monthly[0].claimable").value(false));
        }

        @Test
        @DisplayName("달성하지 않았거나 없는 퀘스트의 보상은 받을 수 없다")
        void cannotClaimUnfinishedOrUnknown() throws Exception {
            지리산_둘레를_칠한다();
            error(mvc.perform(as(post("/quests/leg5/claim"))), 422, "QUEST_NOT_COMPLETED");
            error(mvc.perform(as(post("/quests/nope/claim"))), 404, "QUEST_NOT_FOUND");
        }

        @Test
        @DisplayName("칭호는 얻은 것 중에서만 고를 수 있고, 고르지 않으면 레벨 칭호로 돌아간다")
        void selectEarnedTitleOnly() throws Exception {
            지리산_둘레를_칠한다();
            error(칭호를_고른다("{\"titleId\":\"own-KR-11\"}"), 422, "TITLE_NOT_EARNED");
            error(칭호를_고른다("{\"titleId\":\"zzz\"}"), 404, "TITLE_NOT_FOUND");
            칭호를_고른다("{\"titleId\":\"set-jiri\"}").andExpect(status().isOk()).andExpect(jsonPath("$.title.name").value("산 사람"))
                .andExpect(jsonPath("$.selectedTitleId").value("set-jiri"));
            칭호를_고른다("{\"titleId\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.title.id").value("lv3"));
        }

        @Test
        @DisplayName("한 곳을 취소하면 그곳의 기본 경험치만 빠지고 테마 완성은 남는다")
        void cancelKeepsCompletion() throws Exception {
            지리산_둘레를_칠한다();
            mvc.perform(as(delete("/visits/KR-38380"))).andExpect(status().isNoContent());
            경험치가_될_때까지(ProgressionIntegrationTest.JIRI_XP - 20);
            mvc.perform(as(get("/collection"))).andExpect(jsonPath("$.sets[7].have").value(4))
                .andExpect(jsonPath("$.sets[7].completed").value(true));
        }
    }

    @Test
    @DisplayName("체크인 미리보기는 이 지도 기준 최대 보상이라고 알린다")
    void previewIsMapMaximum() throws Exception {
        mvc.perform(as(get("/visits/preview")).param("region", "KR-38380"))
            .andExpect(jsonPath("$.xp.total").value(45))
            .andExpect(jsonPath("$.xp.basis").value("MAP_MAX"))
            .andExpect(jsonPath("$.xp.note").isNotEmpty());
    }

    @Nested
    @DisplayName("다른 지도의 도감")
    class OtherMaps {

        @Test
        @DisplayName("남의 지도 도감은 멤버가 아니라며 볼 수 없다")
        void othersMap() throws Exception {
            String other = json(mvc.perform(post("/explorers"))).get("personalMapId").asText();
            error(mvc.perform(as(get("/collection")).param("mapId", other)), 403, "NOT_A_MEMBER");
        }

        @Test
        @DisplayName("없는 지도는 없다고 알린다")
        void unknownMap() throws Exception {
            error(mvc.perform(as(get("/collection")).param("mapId", "00000000-0000-0000-0000-000000000000")), 404, "MAP_NOT_FOUND");
        }
    }

    @Test
    @DisplayName("개발용 다시 계산은 소식이 다 전달된 뒤에 돌고 쌓인 결과와 같은 경험치·뱃지를 만든다")
    void devRecalculation() throws Exception {
        칠한다("KR-11010");
        경험치가_될_때까지(35);
        await().atMost(Duration.ofSeconds(10)).until(() -> json(mvc.perform(as(post("/dev/recalculate")))
            .andExpect(status().isOk())).get("recalculated").asInt() == 1);
        assertThat(progress().get("xp").asInt()).isEqualTo(35);
        assertThat(List.of(progress().get("badges").get(0).get("earned").asBoolean())).containsExactly(true);
    }
}
