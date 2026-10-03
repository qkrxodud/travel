package com.kobi.territory.wardrobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import com.kobi.territory.wardrobe.api.event.InviteRewardOwed;
import com.kobi.territory.wardrobe.application.InventoryService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 4단계 QA N1(5단계 처리): 병합(익명 A → 계정 B) 뒤에 늦게 도착한 A 앞 재생 불가 지급(초대 보상)은 B 가 받는다 — 병합 흡수
 * (ExplorerMerged)와 레인이 달라 순서가 보장되지 않아도 비활성 A 의 가방에 남지 않는다. 같은 지급이 다시 와도 한 번(멱등).
 */
@IntegrationTest
class MergedRecipientIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired InventoryService inventories;

    @Test
    void 병합된_익명_탐험가_앞으로_늦게_온_초대_보상은_계정_탐험가가_받는다() throws Exception {
        String email = "n1" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String account = login(null, email).get("explorerId").asText();
        JsonNode device = om.readTree(mvc.perform(post("/explorers")).andExpect(status().isCreated()).andReturn()
            .getResponse().getContentAsString());
        String anonymous = device.get("explorerId").asText();
        JsonNode merged = login(device.get("accessToken").asText(), email);
        assertThat(merged.get("outcome").asText()).isEqualTo("MERGED");

        InviteRewardOwed late = new InviteRewardOwed(anonymous, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            clock.instant());
        inventories.onInviteRewardOwed(late);
        inventories.onInviteRewardOwed(late);

        assertThat(owned(account)).containsOnlyOnce("invite:host-flag");
        assertThat(owned(anonymous)).doesNotContain("invite:host-flag");
    }

    private JsonNode login(String token, String email) throws Exception {
        var request = post("/dev/login").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("email", email)));
        if (token != null) request = request.header("X-Explorer-Token", token);
        return om.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private List<String> owned(String explorerId) {
        return jdbc.queryForList("SELECT item_id FROM owned_item WHERE explorer_id = ?", String.class, explorerId);
    }
}
