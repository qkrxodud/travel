package com.kobi.territory.sharing.domain.privacy;

import static com.kobi.territory.sharing.domain.Fixtures.FRIEND;
import static com.kobi.territory.sharing.domain.Fixtures.OWNER;
import static com.kobi.territory.sharing.domain.Fixtures.STRANGER;
import static com.kobi.territory.sharing.domain.Fixtures.T0;
import static com.kobi.territory.sharing.domain.Fixtures.UNSET;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("여러 사람의 공개 범위")
class PrivacyRosterTest {

    /** OWNER 는 전체 공개, FRIEND 는 친구 공개, UNSET 은 설정한 적 없음. STRANGER 가 본다(STRANGER 자신의 설정은 명단 밖). */
    static final PrivacyRoster ROSTER = PrivacyRoster.of(List.of(OWNER, FRIEND, UNSET), List.of(
        PrivacySettings.restore(OWNER, ProfileVisibility.PUBLIC, T0),
        PrivacySettings.restore(FRIEND, ProfileVisibility.FRIENDS, T0),
        PrivacySettings.restore(STRANGER, ProfileVisibility.PUBLIC, T0)));
    static final ExplorerId VIEWER = STRANGER;

    @Test
    @DisplayName("설정한 적 없는 사람은 비공개로 보고 친구 공개는 친구가 아니면 뺀다")
    void unsetIsPrivate() {
        assertThat(ROSTER.visibleTo(VIEWER, (owner, viewer) -> false)).containsExactly(OWNER);
    }

    @Test
    @DisplayName("친구 공개한 사람은 보는 사람과 서로 팔로우할 때 들어간다")
    void friendsOnlyIncludedForMutual() {
        assertThat(ROSTER.visibleTo(VIEWER, (owner, viewer) -> owner.equals(FRIEND) && viewer.equals(VIEWER)))
            .containsExactlyInAnyOrder(OWNER, FRIEND);
    }
}
