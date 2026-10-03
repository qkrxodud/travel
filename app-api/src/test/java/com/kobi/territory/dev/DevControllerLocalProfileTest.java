package com.kobi.territory.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("로컬 개발 환경")
class DevControllerLocalProfileTest {

    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Nested
    @DisplayName("개발 도구")
    class DevTools {

        @Test
        @DisplayName("켜져 있다")
        void present() {
            assertThat(context.getBeansOfType(DevController.class)).hasSize(1);
        }

        @Test
        @DisplayName("전체 초기화는 누구나 할 수 있다")
        void resetNeedsNoExplorer() throws Exception {
            mvc.perform(delete("/dev/reset")).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("샘플 영토 채우기는 어느 탐험가인지 알아야 한다")
        void seedNeedsExplorer() throws Exception {
            mvc.perform(post("/dev/seed")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EXPLORER_TOKEN_REQUIRED"));
        }
    }

    @Nested
    @DisplayName("서버 시계")
    class ServerClock {

        @AfterEach
        void backToNow() throws Exception {
            mvc.perform(delete("/dev/clock")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("주차·월 넘김을 보려고 앞으로 밀 수 있고 되돌리면 지금으로 돌아온다")
        void advanceAndReset() throws Exception {
            mvc.perform(post("/dev/clock").contentType(MediaType.APPLICATION_JSON).content("{\"days\": 7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offsetSeconds").value(7 * 24 * 3600))
                .andExpect(jsonPath("$.zone").value("Asia/Seoul"));

            mvc.perform(delete("/dev/clock")).andExpect(status().isOk()).andExpect(jsonPath("$.offsetSeconds").value(0));
        }

        @Test
        @DisplayName("정한 시각까지 밀 수 있다")
        void advanceTo() throws Exception {
            String nextYear = OffsetDateTime.now().plusYears(1).withNano(0).toString();

            mvc.perform(post("/dev/clock").contentType(MediaType.APPLICATION_JSON).content("{\"to\": \"" + nextYear + "\"}"))
                .andExpect(status().isOk());
            mvc.perform(get("/dev/clock")).andExpect(jsonPath("$.offsetSeconds").value(greaterThan(300L * 24 * 3600), Long.class));
        }

        @Test
        @DisplayName("뒤로는 밀 수 없다")
        void notBackwards() throws Exception {
            mvc.perform(post("/dev/clock").contentType(MediaType.APPLICATION_JSON).content("{\"to\": \"2000-01-01T00:00:00+09:00\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("전체 초기화는 밀어 둔 시계도 지금으로 되돌린다")
        void resetAlsoResetsClock() throws Exception {
            mvc.perform(post("/dev/clock").contentType(MediaType.APPLICATION_JSON).content("{\"hours\": 5}")).andExpect(status().isOk());

            mvc.perform(delete("/dev/reset")).andExpect(status().isNoContent());

            mvc.perform(get("/dev/clock")).andExpect(jsonPath("$.offsetSeconds").value(0));
        }
    }

    @Nested
    @DisplayName("미스터리 지역 고정")
    class MysteryPin {

        @AfterEach
        void release() throws Exception {
            mvc.perform(put("/dev/mystery").contentType(MediaType.APPLICATION_JSON).content("{\"regionCode\": null}"))
                .andExpect(status().isOk());
            mvc.perform(delete("/dev/clock")).andExpect(status().isOk());
        }

        private String 원래_미스터리() throws Exception {
            return om.readTree(mvc.perform(get("/dev/mystery")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString()).get("regionCode").asText();
        }

        /** 원래 고른 곳과 다른 희귀 지역(기장군 또는 달성군). */
        private String 고정할_지역(String original) {
            return "KR-21310".equals(original) ? "KR-22310" : "KR-21310";
        }

        private void 고정한다(String regionCode) throws Exception {
            mvc.perform(put("/dev/mystery").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"regionCode\": \"" + regionCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionCode").value(regionCode))
                .andExpect(jsonPath("$.pinned").value(true));
        }

        private String 탐험가_토큰() throws Exception {
            return om.readTree(mvc.perform(post("/explorers")).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).get("accessToken").asText();
        }

        @Test
        @DisplayName("고정한 지역이 이번 주 미스터리 지역이 되고 칠하기 전 보상에 미스터리 보너스가 붙는다")
        void pinnedThisWeek() throws Exception {
            String target = 고정할_지역(원래_미스터리());

            고정한다(target);

            String token = 탐험가_토큰();
            mvc.perform(get("/mystery/this-week").header("X-Explorer-Token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.region.code").value(target));
            mvc.perform(get("/visits/preview").param("region", target).header("X-Explorer-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xp.lines[?(@.source == 'MYSTERY_BONUS')]").exists());
        }

        @Test
        @DisplayName("시계를 다음 주로 넘겨도 풀기 전까지 그대로다")
        void staysAcrossWeeks() throws Exception {
            String target = 고정할_지역(원래_미스터리());
            고정한다(target);

            mvc.perform(post("/dev/clock").contentType(MediaType.APPLICATION_JSON).content("{\"days\": 7}")).andExpect(status().isOk());

            mvc.perform(get("/dev/mystery")).andExpect(jsonPath("$.regionCode").value(target)).andExpect(jsonPath("$.pinned").value(true));
        }

        @Test
        @DisplayName("풀면 원래 이번 주 지역으로 돌아간다")
        void unpinRestoresDraw() throws Exception {
            String original = 원래_미스터리();
            고정한다(고정할_지역(original));

            mvc.perform(put("/dev/mystery").contentType(MediaType.APPLICATION_JSON).content("{\"regionCode\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionCode").value(original))
                .andExpect(jsonPath("$.pinned").value(false));
        }

        @Test
        @DisplayName("전체 초기화도 고정을 푼다")
        void resetUnpins() throws Exception {
            String original = 원래_미스터리();
            고정한다(고정할_지역(original));

            mvc.perform(delete("/dev/reset")).andExpect(status().isNoContent());

            mvc.perform(get("/dev/mystery")).andExpect(jsonPath("$.regionCode").value(original)).andExpect(jsonPath("$.pinned").value(false));
        }

        @Test
        @DisplayName("희귀·전설이 아닌 지역이나 없는 지역으로는 고정할 수 없다")
        void onlyRareOrLegend() throws Exception {
            mvc.perform(put("/dev/mystery").contentType(MediaType.APPLICATION_JSON).content("{\"regionCode\": \"KR-11010\"}"))
                .andExpect(status().isBadRequest());
            mvc.perform(put("/dev/mystery").contentType(MediaType.APPLICATION_JSON).content("{\"regionCode\": \"KR-99999\"}"))
                .andExpect(status().isBadRequest());
            mvc.perform(get("/dev/mystery")).andExpect(jsonPath("$.pinned").value(false));
        }
    }

    @Nested
    @DisplayName("서비스 입구")
    class Entrances {

        @Test
        @DisplayName("상태 확인은 하루 상한과 탈퇴 유예 같은 게임 규칙 값을 알려 준다")
        void healthShowsGameRules() throws Exception {
            mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.dailyCap").value(5))
                .andExpect(jsonPath("$.leaveGraceDays").value(7));
        }

        @Test
        @DisplayName("첫 주소는 웹 화면으로 이어진다")
        void rootServesWebApp() throws Exception {
            mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
        }
    }
}
