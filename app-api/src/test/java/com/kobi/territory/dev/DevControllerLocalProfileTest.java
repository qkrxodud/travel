package com.kobi.territory.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class DevControllerLocalProfileTest {

    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;

    @Test
    void local_프로파일에서는_DevController_빈이_존재한다() {
        assertThat(context.getBeansOfType(DevController.class)).hasSize(1);
    }

    @Test
    void dev_reset_과_seed_는_204() throws Exception {
        mvc.perform(delete("/dev/reset")).andExpect(status().isNoContent());
        mvc.perform(post("/dev/seed")).andExpect(status().isNoContent());
    }

    @Test
    void health_는_설정값을_노출한다() throws Exception {
        mvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.dailyCap").value(5))
            .andExpect(jsonPath("$.leaveGraceDays").value(7));
    }

    @Test
    void 루트는_정적_index_html_로_포워드된다() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("index.html"));
    }
}
