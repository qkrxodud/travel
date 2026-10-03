package com.kobi.territory.social.domain.ranking;

import static com.kobi.territory.social.domain.Fixtures.KIM;
import static com.kobi.territory.social.domain.Fixtures.LEE;
import static com.kobi.territory.social.domain.Fixtures.ME;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: §5(친구 랭킹은 탐험가별 중복 제거 지역 수) · §7(친구가 없으면 지역 평균과 비교). */
@DisplayName("친구 랭킹")
class FriendLeaderboardTest {

    @Nested
    @DisplayName("친구가 있으면")
    class WithFriends {

        /** 이 30곳 Lv.9, 김 12곳 Lv.4, 나 12곳 Lv.3. */
        static final FriendLeaderboard BOARD = FriendLeaderboard.of(ME, List.of(
            new FriendScore(KIM, 12, 4), new FriendScore(ME, 12, 3), new FriendScore(LEE, 30, 9)));

        @Test
        @DisplayName("칠한 지역 수가 많은 순서로 선다")
        void byRegionCount() {
            assertThat(BOARD.standings()).extracting(FriendStanding::explorerId).first().isEqualTo(LEE);
        }

        @Test
        @DisplayName("지역 수가 같으면 같은 순위다")
        void sameCountSameRank() {
            assertThat(BOARD.standings()).extracting(FriendStanding::rank).containsExactly(1, 2, 2);
        }

        @Test
        @DisplayName("지역 수가 같으면 레벨이 높은 사람이 먼저 선다")
        void levelBreaksOrder() {
            assertThat(BOARD.standings()).extracting(FriendStanding::explorerId).containsExactly(LEE, KIM, ME);
        }

        @Test
        @DisplayName("내 줄을 표시한다")
        void marksMine() {
            assertThat(BOARD.mine().me()).isTrue();
            assertThat(BOARD.mine().explorerId()).isEqualTo(ME);
        }

        @Test
        @DisplayName("친구 수에는 나를 세지 않는다")
        void friendCountExcludesMe() {
            assertThat(BOARD.friendCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("지역 평균과 비교할 필요가 없다")
        void noBaseline() {
            assertThat(BOARD.needsBaseline()).isFalse();
        }
    }

    @Nested
    @DisplayName("친구가 없으면")
    class Alone {

        static final FriendLeaderboard BOARD = FriendLeaderboard.of(ME, List.of());

        @Test
        @DisplayName("0곳 Lv.1 인 나 한 줄이다")
        void onlyMe() {
            assertThat(BOARD.standings()).containsExactly(new FriendStanding(ME, 0, 1, 1, true));
        }

        @Test
        @DisplayName("지역 평균 탐험가와 비교해야 한다")
        void needsBaseline() {
            assertThat(BOARD.needsBaseline()).isTrue();
        }
    }

    @Test
    @DisplayName("아직 칠한 곳이 없는 나도 0곳 Lv.1 로 친구들 아래에 선다")
    void missingMyScore() {
        FriendLeaderboard board = FriendLeaderboard.of(ME, List.of(new FriendScore(KIM, 3, 2)));

        assertThat(board.standings()).containsExactly(new FriendStanding(KIM, 3, 2, 1, false), new FriendStanding(ME, 0, 1, 2, true));
    }
}
