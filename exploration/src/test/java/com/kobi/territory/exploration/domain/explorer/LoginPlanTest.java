package com.kobi.territory.exploration.domain.explorer;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.THIRD;
import static com.kobi.territory.exploration.domain.explorer.ExplorerFixtures.anonymous;
import static com.kobi.territory.exploration.domain.explorer.ExplorerFixtures.linked;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("로그인 계획")
class LoginPlanTest {

    static LoginPlan.Kind plan(Optional<Explorer> accountExplorer, Optional<Explorer> device) {
        return LoginPlan.decide(accountExplorer, device).kind();
    }

    @Nested
    @DisplayName("계정에 이미 탐험가가 있을 때")
    class KnownAccount {

        @Test
        @DisplayName("이 기기에 활성 익명 탐험가가 있으면 계정으로 합친다")
        void merge() {
            assertThat(plan(Optional.of(linked(FRIEND, "kim")), Optional.of(anonymous(ME)))).isEqualTo(LoginPlan.Kind.MERGE);
        }

        @Test
        @DisplayName("이 기기에 익명 탐험가가 없으면 로그인만 한다")
        void signInWithoutDevice() {
            assertThat(plan(Optional.of(linked(FRIEND, "kim")), Optional.empty())).isEqualTo(LoginPlan.Kind.SIGN_IN);
        }

        @Test
        @DisplayName("이 기기가 이미 그 계정 탐험가면 로그인만 한다")
        void signInSameExplorer() {
            Explorer account = linked(FRIEND, "kim");
            assertThat(plan(Optional.of(account), Optional.of(account))).isEqualTo(LoginPlan.Kind.SIGN_IN);
        }

        @Test
        @DisplayName("이미 합쳐진 기기로 다시 로그인하면 로그인만 한다")
        void signInAfterMerge() {
            Explorer device = anonymous(ME);
            Explorer account = linked(FRIEND, "kim");
            device.mergeInto(account, NOON);
            assertThat(plan(Optional.of(account), Optional.of(device))).isEqualTo(LoginPlan.Kind.SIGN_IN);
        }
    }

    @Nested
    @DisplayName("처음 보는 계정일 때")
    class NewAccount {

        @Test
        @DisplayName("이 기기의 익명 탐험가를 계정에 연결한다")
        void link() {
            assertThat(plan(Optional.empty(), Optional.of(anonymous(ME)))).isEqualTo(LoginPlan.Kind.LINK);
        }

        @Test
        @DisplayName("이 기기에 탐험가가 없으면 새로 만든다")
        void create() {
            assertThat(plan(Optional.empty(), Optional.empty())).isEqualTo(LoginPlan.Kind.CREATE);
        }

        @Test
        @DisplayName("이 기기가 다른 계정의 탐험가면 그 계정은 건드리지 않고 새로 만든다")
        void otherAccountDevice() {
            assertThat(plan(Optional.empty(), Optional.of(linked(THIRD, "lee")))).isEqualTo(LoginPlan.Kind.CREATE);
        }
    }
}
