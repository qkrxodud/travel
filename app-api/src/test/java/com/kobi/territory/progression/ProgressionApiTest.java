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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** D3: 진행 API 계약(MockMvc) — GET /progress, PUT /progress/title, GET /collection, GET /quests, POST /quests/{id}/claim. */
@IntegrationTest
class ProgressionApiTest {

    private static final String H = "X-Explorer-Id";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;

    private String me;
    private String myMap;

    @BeforeEach
    void issue() throws Exception {
        JsonNode body = json(mvc.perform(post("/explorers")).andExpect(status().isCreated()));
        me = body.get("explorerId").asText();
        myMap = body.get("personalMapId").asText();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return om.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder) {
        return builder.header(H, me);
    }

    private void checkIn(String code) throws Exception {
        String body = om.writeValueAsString(Map.of("regionCode", code, "visitDate", LocalDate.now(clock).toString()));
        mvc.perform(as(post("/visits")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    private JsonNode progress() throws Exception {
        return json(mvc.perform(as(get("/progress"))).andExpect(status().isOk()));
    }

    private void awaitXp(int xp) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(progress().get("xp").asInt()).isEqualTo(xp));
    }

    private static void error(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void 탐험가_식별() throws Exception {
        error(mvc.perform(get("/progress")), 401, "EXPLORER_ID_REQUIRED");
        error(mvc.perform(get("/quests").header(H, "not-a-uuid")), 401, "EXPLORER_ID_REQUIRED");
        error(mvc.perform(get("/collection").header(H, "00000000-0000-0000-0000-000000000000")), 404, "EXPLORER_NOT_FOUND");
        error(mvc.perform(get("/progress").header(H, "00000000-0000-0000-0000-000000000000")), 404, "EXPLORER_NOT_FOUND");
    }

    @Test
    void 처음엔_XP_0_Lv1_뱃지12_칭호35_도감9_퀘스트_월간4_상시3() throws Exception {
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
        mvc.perform(as(get("/collection"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.mapId").value(myMap))
            .andExpect(jsonPath("$.completed").value(0))
            .andExpect(jsonPath("$.sets", hasSize(9)))
            .andExpect(jsonPath("$.sets[7].id").value("jiri"))
            .andExpect(jsonPath("$.sets[7].total").value(5))
            .andExpect(jsonPath("$.sets[7].rewardXp").value(100))
            .andExpect(jsonPath("$.sets[7].regions[0].name").value("남원시"));
        mvc.perform(as(get("/quests"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.month").value(YearMonth.now(clock).toString()))
            .andExpect(jsonPath("$.monthly", hasSize(4)))
            .andExpect(jsonPath("$.always", hasSize(3)))
            .andExpect(jsonPath("$.monthly[0].id").value("m3"))
            .andExpect(jsonPath("$.monthly[0].claimable").value(false));
    }

    @Test
    void 체크인_후_진행_도감_퀘스트_보상받기_칭호선택() throws Exception {
        for (String code : ProgressionIntegrationTest.JIRI) checkIn(code);
        awaitXp(ProgressionIntegrationTest.JIRI_XP);

        mvc.perform(as(get("/progress"))).andExpect(jsonPath("$.level").value(4))
            .andExpect(jsonPath("$.levelTitle").value("동네 산책러"))
            .andExpect(jsonPath("$.streak.months").value(1))
            .andExpect(jsonPath("$.streak.activeThisMonth").value(true));
        mvc.perform(as(get("/collection"))).andExpect(jsonPath("$.completed").value(1))
            .andExpect(jsonPath("$.sets[7].have").value(5))
            .andExpect(jsonPath("$.sets[7].completed").value(true))
            .andExpect(jsonPath("$.sets[7].completedAt").isNotEmpty());
        JsonNode questsJson = json(mvc.perform(as(get("/quests"))));
        assertThat(questsJson.get("monthlyDone").asInt()).isEqualTo(4);
        assertThat(questsJson.get("monthly").get(0).get("current").asInt()).isEqualTo(3);
        assertThat(questsJson.get("monthly").get(0).get("claimable").asBoolean()).isTrue();

        // 보상 받기 1회 → XP 는 QuestCompleted 로 비동기 반영
        mvc.perform(as(post("/quests/m3/claim"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.xp").value(60)).andExpect(jsonPath("$.period").value(YearMonth.now(clock).toString()));
        error(mvc.perform(as(post("/quests/m3/claim"))), 409, "QUEST_ALREADY_CLAIMED");
        error(mvc.perform(as(post("/quests/leg5/claim"))), 422, "QUEST_NOT_COMPLETED");
        error(mvc.perform(as(post("/quests/nope/claim"))), 404, "QUEST_NOT_FOUND");
        awaitXp(ProgressionIntegrationTest.JIRI_XP + 60);
        mvc.perform(as(get("/quests"))).andExpect(jsonPath("$.monthly[0].claimed").value(true))
            .andExpect(jsonPath("$.monthly[0].claimable").value(false));

        // 칭호: 얻은 것만
        error(mvc.perform(as(put("/progress/title")).contentType(MediaType.APPLICATION_JSON).content("{\"titleId\":\"own-KR-11\"}")),
            422, "TITLE_NOT_EARNED");
        error(mvc.perform(as(put("/progress/title")).contentType(MediaType.APPLICATION_JSON).content("{\"titleId\":\"zzz\"}")),
            404, "TITLE_NOT_FOUND");
        mvc.perform(as(put("/progress/title")).contentType(MediaType.APPLICATION_JSON).content("{\"titleId\":\"set-jiri\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.title.name").value("산 사람"))
            .andExpect(jsonPath("$.selectedTitleId").value("set-jiri"));
        mvc.perform(as(put("/progress/title")).contentType(MediaType.APPLICATION_JSON).content("{\"titleId\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.title.id").value("lv3"));

        // 취소 → 기본 XP 만 감소, 도감 완성 유지
        mvc.perform(as(delete("/visits/KR-38380"))).andExpect(status().isNoContent());
        awaitXp(ProgressionIntegrationTest.JIRI_XP + 60 - 20);
        mvc.perform(as(get("/collection"))).andExpect(jsonPath("$.sets[7].have").value(4))
            .andExpect(jsonPath("$.sets[7].completed").value(true));
    }

    @Test
    void 미리보기는_지도_기준_최대_보상임을_알린다_D1() throws Exception {
        mvc.perform(as(get("/visits/preview")).param("region", "KR-38380"))
            .andExpect(jsonPath("$.xp.total").value(45))
            .andExpect(jsonPath("$.xp.basis").value("MAP_MAX"))
            .andExpect(jsonPath("$.xp.note").isNotEmpty());
    }

    @Test
    void 남의_지도_도감은_403_없는_지도는_404() throws Exception {
        String other = json(mvc.perform(post("/explorers"))).get("personalMapId").asText();
        error(mvc.perform(as(get("/collection")).param("mapId", other)), 403, "NOT_A_MEMBER");
        error(mvc.perform(as(get("/collection")).param("mapId", "00000000-0000-0000-0000-000000000000")), 404, "MAP_NOT_FOUND");
    }

    @Test
    void dev_재계산() throws Exception {
        checkIn("KR-11010");
        awaitXp(35);
        mvc.perform(as(post("/dev/recalculate"))).andExpect(status().isOk()).andExpect(jsonPath("$.recalculated").value(1));
        assertThat(progress().get("xp").asInt()).isEqualTo(35);
        assertThat(List.of(progress().get("badges").get(0).get("earned").asBoolean())).containsExactly(true);
    }
}
