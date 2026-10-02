package com.kobi.territory.exploration.domain;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 일급 컬렉션 Members — Spring 없음. */
class MembersTest {

    static ExplorationError errorOf(Throwable t) {
        return ((ExplorationException) t).error();
    }

    @Test
    void 멤버십_판정() {
        Members m = Members.ownerOnly(new Member(ME, MemberRole.OWNER, NOON));
        assertThat(m.require(ME).role()).isEqualTo(MemberRole.OWNER);
        assertThat(m.owner().explorerId()).isEqualTo(ME);
        assertThatThrownBy(() -> m.require(FRIEND)).satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.NOT_A_MEMBER));
    }

    @Test
    void 최대_4명() {
        List<Member> list = new ArrayList<>(List.of(new Member(ME, MemberRole.OWNER, NOON)));
        for (int i = 0; i < 3; i++) list.add(new Member(ExplorerId.newId(), MemberRole.MEMBER, NOON));
        assertThat(Members.of(list).size()).isEqualTo(4);
        list.add(new Member(ExplorerId.newId(), MemberRole.MEMBER, NOON));
        assertThatThrownBy(() -> Members.of(list)).satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.MAP_FULL));
    }

    @Test
    void OWNER는_정확히_1명_중복_가입_불가() {
        assertThatThrownBy(() -> Members.of(List.of(new Member(ME, MemberRole.MEMBER, NOON))))
            .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.INVALID_MAP));
        assertThatThrownBy(() -> Members.of(List.of(new Member(ME, MemberRole.OWNER, NOON), new Member(FRIEND, MemberRole.OWNER, NOON))))
            .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.INVALID_MAP));
        assertThatThrownBy(() -> Members.of(List.of(new Member(ME, MemberRole.OWNER, NOON), new Member(ME, MemberRole.MEMBER, NOON))))
            .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.ALREADY_MEMBER));
    }
}
