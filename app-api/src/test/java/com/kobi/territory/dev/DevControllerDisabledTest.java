package com.kobi.territory.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kobi.territory.exploration.application.ExplorationDevService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * QA P3-11: local 프로파일이어도 territory.dev.enabled 가 true 가 아니면 /dev/**(로그인 포함) 빈이 없다 — 기본 프로파일(local)에만
 * 기대지 않는 두 번째 안전장치.
 */
@SpringBootTest(properties = {"territory.dev.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:dev-disabled;MODE=MySQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class DevControllerDisabledTest {

    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;

    @Test
    void dev_enabled_가_없으면_dev_엔드포인트가_없다() throws Exception {
        assertThat(context.getBeansOfType(DevController.class)).isEmpty();
        assertThat(context.getBeansOfType(ExplorationDevService.class)).isEmpty();
        mvc.perform(post("/dev/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@example.com\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/dev/reset")).andExpect(status().isNotFound());
    }
}
