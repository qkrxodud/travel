package com.kobi.territory.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kobi.territory.catalog.api.query.MysteryRegionQuery;
import com.kobi.territory.catalog.application.MysteryService;
import com.kobi.territory.exploration.application.ExplorationDevService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 회귀 출처 QA P3-11: local 프로파일이어도 territory.dev.enabled 가 true 가 아니면 /dev/**(로그인 포함) 빈이 없다 — 기본 프로파일(local)에만
 * 기대지 않는 두 번째 안전장치.
 */
@SpringBootTest(properties = {"territory.dev.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:dev-disabled;MODE=MySQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@DisplayName("개발 도구를 끈 로컬 환경")
class DevControllerDisabledTest {

    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("개발용 로그인과 초기화 입구가 아예 없고 미스터리 지역은 원래 주차 선택 그대로다")
    void devEntrancesDoNotExist() throws Exception {
        assertThat(context.getBeansOfType(DevController.class)).isEmpty();
        assertThat(context.getBeansOfType(ExplorationDevService.class)).isEmpty();
        assertThat(context.getBeansOfType(PinnableMysteryRegionQuery.class)).isEmpty();
        assertThat(context.getBean(MysteryRegionQuery.class)).isInstanceOf(MysteryService.class);
        mvc.perform(post("/dev/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@example.com\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/dev/reset")).andExpect(status().isNotFound());
    }
}
