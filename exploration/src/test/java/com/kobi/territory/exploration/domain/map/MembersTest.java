package com.kobi.territory.exploration.domain.map;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.refusal;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.ExplorationError;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("지도 멤버 명단")
class MembersTest {

    static Member owner(ExplorerId id) {
        return new Member(id, MemberRole.OWNER, NOON);
    }

    static Member member(ExplorerId id) {
        return new Member(id, MemberRole.MEMBER, NOON);
    }

    @Nested
    @DisplayName("멤버인지 확인할 때")
    class Membership {

        @Test
        @DisplayName("명단에 있으면 역할과 함께 확인된다")
        void found() {
            Members members = Members.ownerOnly(owner(ME));
            assertThat(members.require(ME).role()).isEqualTo(MemberRole.OWNER);
            assertThat(members.owner().explorerId()).isEqualTo(ME);
        }

        @Test
        @DisplayName("명단에 없으면 멤버가 아니라고 거절된다")
        void notFound() {
            assertThat(refusal(() -> Members.ownerOnly(owner(ME)).require(FRIEND))).isEqualTo(ExplorationError.NOT_A_MEMBER);
        }
    }

    @Nested
    @DisplayName("인원")
    class Size {

        @Test
        @DisplayName("넷까지 둘 수 있다")
        void four() {
            List<Member> list = new ArrayList<>(List.of(owner(ME)));
            for (int i = 0; i < 3; i++) list.add(member(ExplorerId.newId()));
            assertThat(Members.of(list).size()).isEqualTo(4);
        }

        @Test
        @DisplayName("다섯은 가득 찼다고 거절된다")
        void fiveRefused() {
            List<Member> list = new ArrayList<>(List.of(owner(ME)));
            for (int i = 0; i < 4; i++) list.add(member(ExplorerId.newId()));
            assertThat(refusal(() -> Members.of(list))).isEqualTo(ExplorationError.MAP_FULL);
        }
    }

    @Nested
    @DisplayName("지도장")
    class Owner {

        @Test
        @DisplayName("지도장이 없는 명단은 잘못된 지도다")
        void none() {
            assertThat(refusal(() -> Members.of(List.of(member(ME))))).isEqualTo(ExplorationError.INVALID_MAP);
        }

        @Test
        @DisplayName("지도장이 둘인 명단은 잘못된 지도다")
        void two() {
            assertThat(refusal(() -> Members.of(List.of(owner(ME), owner(FRIEND))))).isEqualTo(ExplorationError.INVALID_MAP);
        }
    }

    @Test
    @DisplayName("같은 사람이 두 번 오를 수 없다")
    void duplicate() {
        assertThat(refusal(() -> Members.of(List.of(owner(ME), member(ME))))).isEqualTo(ExplorationError.ALREADY_MEMBER);
    }
}
