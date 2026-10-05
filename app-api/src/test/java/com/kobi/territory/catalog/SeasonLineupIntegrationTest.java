package com.kobi.territory.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kobi.territory.catalog.application.SeasonLineupCache;
import com.kobi.territory.catalog.application.SeasonLineupService;
import com.kobi.territory.support.Explorers;
import com.kobi.territory.support.Explorers.Anonymous;
import com.kobi.territory.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 계절 명소 근거 — TourAPI 키가 없을 때(지금): 계절 회차는 지금과 똑같이 기본 목록을 쓰고 "AI 추정"으로 표시된다. 관리자 화면은 키가 없다고
 * 알려 주고, 아무것도 부르지 않는다. 시계는 2026-10-02(가을 회차 진행 중).
 */
@IntegrationTest
@DisplayName("계절 명소 근거 — 키가 없을 때")
class SeasonLineupIntegrationTest {

    private static final String ADMIN = "X-Admin-Token";
    private static final String TOKEN = "local-admin-token";
    /** 가을 단풍 명소 기본 목록(seasons.json, AI 추정). */
    private static final List<String> 단풍_기본 = List.of("KR-32060", "KR-35040", "KR-32340", "KR-37330", "KR-36450", "KR-33320",
        "KR-38400", "KR-11090", "KR-31370", "KR-35310");

    @Autowired MockMvc mvc;
    @Autowired Explorers explorers;
    @Autowired JdbcTemplate jdbc;
    @Autowired SeasonLineupService lineups;
    @Autowired SeasonLineupCache cache;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM season_lineup_region WHERE round_id NOT IN ('spring-2026', 'autumn-2026')");
        jdbc.update("DELETE FROM season_lineup WHERE round_id NOT IN ('spring-2026', 'autumn-2026')");
        cache.invalidate();
    }

    private JsonNode 관리자(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int expectedStatus)
        throws Exception {
        return explorers.json(mvc.perform(request.header(ADMIN, TOKEN)).andExpect(status().is(expectedStatus)));
    }

    @Nested
    @DisplayName("탐험가가 보는 계절 회차")
    class Explorer {

        @Test
        @DisplayName("진행 중인 가을 회차는 기본 열 곳 그대로이고 지역마다 AI 추정으로 표시되며 근거 축제는 없다")
        void currentRoundIsAiEstimate() throws Exception {
            Anonymous 탐험가 = explorers.익명_탐험가();
            JsonNode 계절 = explorers.json(explorers.기기로(탐험가, get("/seasons/current")).andExpect(status().isOk()));

            JsonNode 가을 = 계절.get("current").get(0);
            assertThat(가을.get("roundId").asText()).isEqualTo("autumn-2026");
            assertThat(가을.get("provenance").asText()).isEqualTo("ai-estimate");
            assertThat(가을.get("source").isNull()).isTrue();
            assertThat(가을.get("regions").findValuesAsText("code")).containsExactlyElementsOf(단풍_기본);
            가을.get("regions").forEach(region -> {
                assertThat(region.get("provenance").asText()).isEqualTo("ai-estimate");
                assertThat(region.get("evidence")).isEmpty();
            });
            assertThat(계절.get("next").get("provenance").asText()).isEqualTo("ai-estimate");
        }

        @Test
        @DisplayName("기본 목록의 지역을 칠하면 지금처럼 회차에 센다")
        void countsAsBefore() throws Exception {
            Anonymous 탐험가 = explorers.익명_탐험가();
            explorers.칠한다(탐험가, "KR-32060");
            explorers.전달이_끝날_때까지();

            explorers.기기로(탐험가, get("/seasons/current")).andExpect(jsonPath("$.current[0].have").value(1))
                .andExpect(jsonPath("$.current[0].regions[0].collected").value(true));
        }
    }

    @Nested
    @DisplayName("관리자 화면")
    class Admin {

        @Test
        @DisplayName("키가 없다고 알리고, 진행 중인 회차(고정)와 계절마다 다음 회차를 보여 준다")
        void overview() throws Exception {
            JsonNode 화면 = 관리자(get("/admin/seasons"), 200);

            assertThat(화면.get("tourApi").get("configured").asBoolean()).isFalse();
            assertThat(화면.get("tourApi").get("warnings").get(0).asText()).contains("TOURAPI_SERVICE_KEY");
            assertThat(화면.get("rounds").findValuesAsText("roundId")).containsExactly("autumn-2026", "spring-2027", "autumn-2027");
            JsonNode 진행중 = 화면.get("rounds").get(0);
            assertThat(진행중.get("locked").asBoolean()).isTrue();
            assertThat(진행중.get("inEffect").get("provenance").asText()).isEqualTo("ai-estimate");
            assertThat(진행중.get("inEffect").get("confirmedBy").asText()).isEqualTo("OPENING");
            assertThat(화면.get("rounds").get(1).get("warnings").get(0).asText()).contains("AI 추정 목록을 그대로");
        }

        @Test
        @DisplayName("다음 회차를 갱신해도 아무것도 부르지 않고 키가 없다는 까닭을 남긴다 — 목록은 그대로")
        void refreshWithoutKey() throws Exception {
            JsonNode 회차 = 관리자(post("/admin/seasons/spring-2027/refresh"), 200);

            assertThat(회차.get("lastAttempt").get("outcome").asText()).isEqualTo("NOT_CONFIGURED");
            assertThat(회차.get("inEffect").get("provenance").asText()).isEqualTo("ai-estimate");
            assertThat(회차.get("candidate").isNull()).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tourapi_usage", Integer.class)).isZero();
        }

        @Test
        @DisplayName("후보 없이 확정할 수 없다")
        void confirmWithoutCandidate() throws Exception {
            mvc.perform(put("/admin/seasons/spring-2027/confirm").header(ADMIN, TOKEN)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_CANDIDATE_MISSING"));
        }

        @Test
        @DisplayName("진행 중인 회차는 갱신할 수 없다 — 갱신은 다음 회차부터")
        void lockedRound() throws Exception {
            mvc.perform(post("/admin/seasons/autumn-2026/refresh").header(ADMIN, TOKEN)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_ROUND_LOCKED"));
        }

        @Test
        @DisplayName("모르는 회차는 찾을 수 없다")
        void unknownRound() throws Exception {
            mvc.perform(get("/admin/seasons/winter-2027").header(ADMIN, TOKEN)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASON_ROUND_NOT_FOUND"));
        }

        @Test
        @DisplayName("관리자 토큰이 없으면 볼 수 없다")
        void needsToken() throws Exception {
            mvc.perform(get("/admin/seasons")).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("키가 없으면 자동 수집은 아무것도 하지 않는다")
    void noAutomaticCollection() {
        assertThat(lineups.collectAutomatically()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM season_lineup WHERE round_id NOT IN ('spring-2026', 'autumn-2026')",
            Integer.class)).isZero();
    }

    @Nested
    @DisplayName("열린 회차의 고정")
    class Snapshot {

        @Test
        @DisplayName("이미 진행 중·지난 2026 회차는 그때의 기본 열 곳으로 고정돼 있다")
        void migratedRounds() {
            assertThat(jdbc.queryForList("SELECT round_id FROM season_lineup WHERE confirmed_by = 'OPENING' ORDER BY round_id", String.class))
                .contains("autumn-2026", "spring-2026");
            assertThat(jdbc.queryForList("SELECT region_code FROM season_lineup_region WHERE round_id = 'autumn-2026' AND stage = 'CONFIRMED'"
                + " ORDER BY position_no", String.class)).containsExactlyElementsOf(단풍_기본);
        }

        @Test
        @DisplayName("고정 기록이 없는 열린 회차는 처음 볼 때 그때의 기본 목록으로 고정된다")
        void snapshotOnFirstLook() throws Exception {
            jdbc.update("DELETE FROM season_lineup_region WHERE round_id = 'autumn-2026'");
            jdbc.update("DELETE FROM season_lineup WHERE round_id = 'autumn-2026'");
            cache.invalidate();

            Anonymous 탐험가 = explorers.익명_탐험가();
            explorers.기기로(탐험가, get("/seasons/current")).andExpect(status().isOk());

            assertThat(jdbc.queryForObject("SELECT confirmed_by FROM season_lineup WHERE round_id = 'autumn-2026'", String.class))
                .isEqualTo("OPENING");
            assertThat(jdbc.queryForList("SELECT region_code FROM season_lineup_region WHERE round_id = 'autumn-2026' AND stage = 'CONFIRMED'"
                + " ORDER BY position_no", String.class)).containsExactlyElementsOf(단풍_기본);
        }

        @Test
        @DisplayName("기동할 때·하루 한 번 열린 회차를 고정한다(키와 무관)")
        void snapshotOpenRounds() {
            jdbc.update("DELETE FROM season_lineup_region WHERE round_id = 'autumn-2026'");
            jdbc.update("DELETE FROM season_lineup WHERE round_id = 'autumn-2026'");

            lineups.snapshotOpenRounds();

            assertThat(jdbc.queryForObject("SELECT confirmed_by FROM season_lineup WHERE round_id = 'autumn-2026'", String.class))
                .isEqualTo("OPENING");
        }
    }
}
