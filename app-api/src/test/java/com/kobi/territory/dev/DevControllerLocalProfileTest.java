package com.kobi.territory.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("로컬 개발 환경")
class DevControllerLocalProfileTest {

    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;

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
