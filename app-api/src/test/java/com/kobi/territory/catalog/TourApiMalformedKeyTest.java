package com.kobi.territory.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kobi.territory.catalog.application.SeasonLineupService;
import com.kobi.territory.support.IntegrationTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 계절 명소 근거 — 운영자가 키·주소를 잘못 붙였을 때: 연동은 꺼지고(기본 목록 그대로), 자동 수집·관리자 갱신 어느 길로도 키가 기록·응답에 남지 않는다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:territory-badkey;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "territory.tourapi.service-key=QALEAKMARK0123%zz",
    "territory.tourapi.collect.on-startup=false",
    "territory.tourapi.collect.cron=-"})
@AutoConfigureMockMvc
@Import(IntegrationTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("계절 명소 근거 — 끝이 잘린 서비스 키를 넣어도")
class TourApiMalformedKeyTest {

    @Autowired MockMvc mvc;
    @Autowired SeasonLineupService lineups;

    @Test
    @DisplayName("연동이 꺼지고 아무것도 모으지 않으며, 자동 수집·관리자 갱신 어디에도 키가 남지 않는다")
    void neverLeaksKey(CapturedOutput output) throws Exception {
        assertThat(lineups.configured()).isFalse();
        assertThat(lineups.collectAutomatically()).isEmpty();

        String refreshed = mvc.perform(post("/admin/seasons/spring-2027/refresh").header("X-Admin-Token", "local-admin-token"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String overview = mvc.perform(get("/admin/seasons").header("X-Admin-Token", "local-admin-token"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(refreshed).contains("NOT_CONFIGURED").contains("서비스 키 형식 오류").doesNotContain("QALEAKMARK");
        assertThat(overview).contains("서비스 키 형식 오류").doesNotContain("QALEAKMARK");
        assertThat(output.getAll()).doesNotContain("QALEAKMARK");
    }
}
