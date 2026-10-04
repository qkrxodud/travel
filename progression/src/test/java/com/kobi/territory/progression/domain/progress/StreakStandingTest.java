package com.kobi.territory.progression.domain.progress;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("이번 달을 놓치면 끊길 연속 탐험")
class StreakStandingTest {

    private static final ExplorerId 탐험가 = ExplorerId.of("11111111-1111-1111-1111-111111111111");
    private static final YearMonth 시월 = YearMonth.of(2026, 10);

    private StreakStanding 상태(Streak streak, int freezes) {
        return new StreakStanding(탐험가, streak, freezes);
    }

    @Nested
    @DisplayName("지난달까지 이어 왔고 이번 달에 아직 새 지역이 없으면")
    class NotYetThisMonth {

        @Test
        @DisplayName("이번 달을 놓치면 끊길 수 있다 — 스트릭 지키기 알림 대상")
        void atRisk() {
            StreakStanding standing = 상태(Streak.of(5, 시월.minusMonths(1)), 0);

            assertThat(standing.atRiskIn(시월)).isTrue();
            assertThat(standing.monthsAsOf(시월)).isEqualTo(5);
            assertThat(standing.checkedInIn(시월)).isFalse();
        }

        @Test
        @DisplayName("이번 달을 놓치면 다음 달에 보호권 하나가 든다")
        void oneFreezeIfMissed() {
            assertThat(상태(Streak.of(5, 시월.minusMonths(1)), 2).freezesNeededIfMissed(시월)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("빈 달이 있지만 보호권으로 메울 수 있으면")
    class BridgeableGap {

        @Test
        @DisplayName("아직 이어진 연속이라 놓치면 끊길 수 있고, 놓치면 보호권이 하나 더 든다")
        void stillAtRisk() {
            StreakStanding standing = 상태(Streak.of(3, 시월.minusMonths(2)), 1);

            assertThat(standing.atRiskIn(시월)).isTrue();
            assertThat(standing.freezesNeededIfMissed(시월)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("알림 대상이 아닌 경우")
    class NotAtRisk {

        @Test
        @DisplayName("이번 달에 이미 새 지역을 칠했으면 대상이 아니다")
        void alreadyThisMonth() {
            StreakStanding standing = 상태(Streak.of(6, 시월), 0);

            assertThat(standing.atRiskIn(시월)).isFalse();
            assertThat(standing.checkedInIn(시월)).isTrue();
        }

        @Test
        @DisplayName("이미 끊겨 이어 갈 연속이 없으면 대상이 아니다")
        void alreadyBroken() {
            assertThat(상태(Streak.of(4, 시월.minusMonths(3)), 1).atRiskIn(시월)).isFalse();
        }

        @Test
        @DisplayName("한 번도 칠한 적이 없으면 대상이 아니다")
        void neverStarted() {
            assertThat(상태(Streak.NONE, 0).atRiskIn(시월)).isFalse();
        }
    }
}
