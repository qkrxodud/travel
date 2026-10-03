package com.kobi.territory.wardrobe.domain.inventory;

import static com.kobi.territory.wardrobe.domain.Fixtures.HOST;
import static com.kobi.territory.wardrobe.domain.Fixtures.ME;
import static com.kobi.territory.wardrobe.domain.Fixtures.OTHER_HOST;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 3단계 QA P3-R2-5(완성 직후 합류자 테마 보상). */
@DisplayName("완성 직후 합류한 멤버의 테마 보상")
class ThemeRewardRecipientsTest {

    /** 내가 완성했고 그때 멤버는 나와 HOST, 그 뒤 OTHER_HOST 가 합류했다. */
    static final ExplorerId COMPLETER = ME;
    static final ExplorerId LATE = OTHER_HOST;
    static final List<ExplorerId> RECIPIENTS = List.of(COMPLETER, HOST);
    static final List<ExplorerId> MEMBERS_NOW = List.of(COMPLETER, HOST, LATE);

    @Test
    @DisplayName("완성자 몫을 처리할 때 완성 시점 수령자가 아닌 지금 멤버를 챙긴다")
    void completerShareFindsLateJoiners() {
        assertThat(ThemeRewardRecipients.lateJoiners(COMPLETER, COMPLETER, RECIPIENTS, MEMBERS_NOW)).containsExactly(LATE);
    }

    @Test
    @DisplayName("다른 수령자 몫에서는 따로 챙기지 않아 한 번만 준다")
    void otherRecipientShareFindsNobody() {
        assertThat(ThemeRewardRecipients.lateJoiners(HOST, COMPLETER, RECIPIENTS, MEMBERS_NOW)).isEmpty();
    }

    @Test
    @DisplayName("수령자 명단이 없는 예전 완성은 완성자 한 명만 받은 것으로 본다")
    void legacyCompletionTreatsOnlyCompleterAsRecipient() {
        assertThat(ThemeRewardRecipients.lateJoiners(COMPLETER, null, null, MEMBERS_NOW)).containsExactly(HOST, LATE);
    }
}
