package com.kobi.territory.wardrobe;

import static com.kobi.territory.support.Explorers.새_이메일;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.support.Explorers;
import com.kobi.territory.support.Explorers.Anonymous;
import com.kobi.territory.support.Explorers.Session;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import com.kobi.territory.wardrobe.api.event.InviteRewardOwed;
import com.kobi.territory.wardrobe.application.InventoryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 회귀 출처 4단계 QA N1(5단계 처리): 병합(익명 A → 계정 B) 뒤에 늦게 도착한 A 앞 재생 불가 지급(초대 보상)은 B 가 받는다 — 병합 흡수
 * (ExplorerMerged)와 레인이 달라 순서가 보장되지 않아도 비활성 A 의 가방에 남지 않는다. 같은 지급이 다시 와도 한 번(멱등).
 */
@IntegrationTest
@DisplayName("합친 뒤 늦게 온 보상")
class MergedRecipientIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired InventoryService inventories;
    @Autowired Explorers explorers;

    @Test
    @DisplayName("계정으로 합쳐진 익명 탐험가 앞으로 늦게 온 초대 보상은 계정 탐험가가 한 번만 받는다")
    void lateRewardGoesToAccountOnce() throws Exception {
        String email = 새_이메일("n1");
        String account = explorers.로그인(null, email).explorerId();
        Anonymous device = explorers.익명_탐험가();
        Session merged = explorers.로그인(device, email);
        assertThat(merged.outcome()).isEqualTo("MERGED");

        InviteRewardOwed late = new InviteRewardOwed(device.id(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            clock.instant());
        inventories.onInviteRewardOwed(late);
        inventories.onInviteRewardOwed(late);

        assertThat(가방(account)).containsOnlyOnce("invite:host-flag");
        assertThat(가방(device.id())).doesNotContain("invite:host-flag");
    }

    private List<String> 가방(String explorerId) {
        return jdbc.queryForList("SELECT item_id FROM owned_item WHERE explorer_id = ?", String.class, explorerId);
    }
}
