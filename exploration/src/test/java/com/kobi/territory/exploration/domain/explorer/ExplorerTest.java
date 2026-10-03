package com.kobi.territory.exploration.domain.explorer;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.THIRD;
import static com.kobi.territory.exploration.domain.Fixtures.refusal;
import static com.kobi.territory.exploration.domain.explorer.ExplorerFixtures.KIM;
import static com.kobi.territory.exploration.domain.explorer.ExplorerFixtures.RESERVE;
import static com.kobi.territory.exploration.domain.explorer.ExplorerFixtures.anonymous;
import static com.kobi.territory.exploration.domain.explorer.ExplorerFixtures.linked;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.exploration.domain.ExplorationError;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 탐험가의 계정 연결·핸들 변경·기존 계정으로 합치기. 회귀 출처: 4단계 D1·P3-10. */
@DisplayName("탐험가")
class ExplorerTest {

    @Nested
    @DisplayName("익명 탐험가가 처음 계정을 연결할 때")
    class Link {

        @Test
        @DisplayName("핸들이 생기고 공개 프로필을 가진 계정 탐험가가 된다")
        void becomesAccountExplorer() {
            Explorer explorer = anonymous(ME);
            assertThat(explorer.linkable()).isTrue();
            Explorer.HandleChange change = explorer.linkAccount(new Account(KIM, NOON), new Handle("kim_traveler"));
            assertThat(change.previous()).isNull();
            assertThat(explorer.anonymous()).isFalse();
            assertThat(explorer.publicProfile()).isTrue();
        }

        @Test
        @DisplayName("이 기기의 익명 토큰은 더 쓸 수 없다")
        void tokenRevoked() {
            Explorer explorer = anonymous(ME);
            explorer.linkAccount(new Account(KIM, NOON), new Handle("kim_traveler"));
            assertThat(explorer.tokenHash()).isNull();
        }

        @Test
        @DisplayName("계정 이메일은 소문자로 기록된다")
        void emailLowercased() {
            Explorer explorer = anonymous(ME);
            explorer.linkAccount(new Account(KIM, NOON), new Handle("kim_traveler"));
            assertThat(explorer.account()).map(account -> account.identity().email()).contains("kim.traveler+tag@example.com");
        }

        @Test
        @DisplayName("이미 연결된 탐험가는 다시 연결할 수 없다")
        void twiceRefused() {
            Explorer explorer = anonymous(ME);
            explorer.linkAccount(new Account(KIM, NOON), new Handle("kim_traveler"));
            assertThat(refusal(() -> explorer.linkAccount(new Account(KIM, NOON), new Handle("again"))))
                .isEqualTo(ExplorationError.MERGE_NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("계정 신원")
    class Identity {

        @Test
        @DisplayName("제공자 이름은 소문자 영문이어야 한다")
        void provider() {
            assertThat(refusal(() -> new AccountIdentity("Google!", "sub", "a@b.c"))).isEqualTo(ExplorationError.ACCOUNT_INVALID);
        }

        @Test
        @DisplayName("계정 식별자를 비울 수 없다")
        void subject() {
            assertThat(refusal(() -> new AccountIdentity("google", " ", "a@b.c"))).isEqualTo(ExplorationError.ACCOUNT_INVALID);
        }

        @Test
        @DisplayName("이메일 형식이어야 한다")
        void email() {
            assertThat(refusal(() -> new AccountIdentity("google", "sub", "no-at"))).isEqualTo(ExplorationError.ACCOUNT_INVALID);
        }
    }

    @Nested
    @DisplayName("핸들을 바꿀 때")
    class ChangeHandle {

        @Test
        @DisplayName("새 핸들로 바뀌고 이전 핸들을 알려준다")
        void changes() {
            Explorer explorer = linked(FRIEND, "lee");
            Optional<Explorer.HandleChange> change = explorer.changeHandle(new Handle("lee_2"), NOON, RESERVE, handle -> false);
            assertThat(change).map(Explorer.HandleChange::previous).contains(new Handle("lee"));
            assertThat(explorer.handle()).isEqualTo(new Handle("lee_2"));
        }

        @Test
        @DisplayName("같은 핸들로 바꾸면 아무 변화가 없다")
        void same() {
            assertThat(linked(FRIEND, "lee").changeHandle(new Handle("lee"), NOON, RESERVE, handle -> true)).isEmpty();
        }

        @Test
        @DisplayName("익명 탐험가는 로그인해야 바꿀 수 있다")
        void anonymousRefused() {
            assertThat(refusal(() -> anonymous(ME).changeHandle(new Handle("kim"), NOON, RESERVE, handle -> false)))
                .isEqualTo(ExplorationError.LOGIN_REQUIRED);
        }

        @Test
        @DisplayName("다른 탐험가가 쓰는 핸들로는 바꿀 수 없다")
        void takenRefused() {
            assertThat(refusal(() -> linked(FRIEND, "lee").changeHandle(new Handle("park"), NOON, RESERVE, handle -> true)))
                .isEqualTo(ExplorationError.HANDLE_TAKEN);
        }

        @Nested
        @DisplayName("놓은 핸들은")
        class Reservation {

            @Test
            @DisplayName("예약 기간 동안 남이 가져갈 수 없게 예약된다")
            void reserved() {
                Explorer explorer = linked(FRIEND, "lee");
                explorer.changeHandle(new Handle("lee_2"), NOON, RESERVE, handle -> false);
                assertThat(explorer.handleReservations()).containsExactly(new HandleReservation(new Handle("lee"), NOON.plus(RESERVE)));
            }

            @Test
            @DisplayName("예약은 기한 직전까지 살아 있고 기한에 끝난다")
            void expiresAtDeadline() {
                Explorer explorer = linked(FRIEND, "lee");
                explorer.changeHandle(new Handle("lee_2"), NOON, RESERVE, handle -> false);
                HandleReservation reservation = explorer.handleReservations().get(0);
                assertThat(reservation.activeAt(NOON.plus(RESERVE).minusSeconds(1))).isTrue();
                assertThat(reservation.activeAt(NOON.plus(RESERVE))).isFalse();
            }

            @Test
            @DisplayName("본인은 되돌릴 수 있고 그러면 방금 놓은 핸들이 예약된다")
            void ownerCanRevert() {
                Explorer explorer = linked(FRIEND, "lee");
                explorer.changeHandle(new Handle("lee_2"), NOON, RESERVE, handle -> false);
                explorer.changeHandle(new Handle("lee"), NOON.plusSeconds(60), RESERVE, handle -> false);
                assertThat(explorer.handleReservations()).extracting(HandleReservation::handle).containsExactly(new Handle("lee_2"));
            }

            @Test
            @DisplayName("기한이 지난 예약은 다음 변경 때 정리된다")
            void expiredCleaned() {
                Explorer explorer = linked(FRIEND, "lee");
                explorer.changeHandle(new Handle("lee_2"), NOON, RESERVE, handle -> false);
                explorer.changeHandle(new Handle("lee"), NOON.plusSeconds(60), RESERVE, handle -> false);
                explorer.changeHandle(new Handle("lee_3"), NOON.plus(RESERVE).plusSeconds(120), RESERVE, handle -> false);
                assertThat(explorer.handleReservations()).extracting(HandleReservation::handle).containsExactly(new Handle("lee"));
            }
        }
    }

    @Nested
    @DisplayName("기존 계정으로 합칠 때")
    class MergeInto {

        @Test
        @DisplayName("활성 익명 탐험가는 다른 활성 계정 탐험가로 합쳐진다")
        void merges() {
            Explorer device = anonymous(ME);
            Explorer account = linked(FRIEND, "kim");
            assertThat(device.canMergeInto(account)).isTrue();
            assertThat(device.mergeInto(account, NOON)).map(Explorer.Merge::into).contains(FRIEND);
        }

        @Test
        @DisplayName("합쳐진 익명 탐험가는 비활성이 되고 토큰을 더 쓸 수 없다")
        void deactivated() {
            Explorer device = anonymous(ME);
            device.mergeInto(linked(FRIEND, "kim"), NOON);
            assertThat(device.active()).isFalse();
            assertThat(device.status()).isEqualTo(ExplorerStatus.MERGED);
            assertThat(device.mergedInto()).contains(FRIEND);
            assertThat(device.tokenHash()).isNull();
        }

        @Test
        @DisplayName("같은 계정으로 다시 합쳐도 아무 일도 없다")
        void idempotent() {
            Explorer device = anonymous(ME);
            Explorer account = linked(FRIEND, "kim");
            device.mergeInto(account, NOON);
            assertThat(device.mergeInto(account, NOON.plusSeconds(1))).isEmpty();
            assertThat(device.mergedAt()).isEqualTo(NOON);
        }

        @Test
        @DisplayName("한 번 합쳐지면 다른 계정으로 옮겨 갈 수 없다")
        void noSecondTarget() {
            Explorer device = anonymous(ME);
            device.mergeInto(linked(FRIEND, "kim"), NOON);
            assertThat(refusal(() -> device.mergeInto(linked(THIRD, "lee"), NOON))).isEqualTo(ExplorationError.MERGE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("이미 계정 탐험가인 쪽은 합쳐지지 않는다")
        void linkedCannotMerge() {
            assertThat(linked(ME, "lee").canMergeInto(linked(FRIEND, "kim"))).isFalse();
        }

        @Test
        @DisplayName("익명 탐험가에게는 합칠 수 없다")
        void anonymousTargetRefused() {
            assertThat(anonymous(ME).canMergeInto(anonymous(THIRD))).isFalse();
            assertThat(refusal(() -> anonymous(ME).mergeInto(anonymous(THIRD), NOON))).isEqualTo(ExplorationError.MERGE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("자기 자신에게는 합칠 수 없다")
        void selfRefused() {
            Explorer account = linked(FRIEND, "kim");
            assertThat(account.canMergeInto(account)).isFalse();
        }
    }
}
