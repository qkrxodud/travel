package com.kobi.territory.progression.domain.progress;

import static com.kobi.territory.progression.domain.Fixtures.가평군;
import static com.kobi.territory.progression.domain.Fixtures.다른지도;
import static com.kobi.territory.progression.domain.Fixtures.방문;
import static com.kobi.territory.progression.domain.Fixtures.새_진행;
import static com.kobi.territory.progression.domain.Fixtures.종로구;
import static com.kobi.territory.progression.domain.Fixtures.초;
import static com.kobi.territory.progression.domain.Fixtures.취소한다;
import static com.kobi.territory.progression.domain.Fixtures.칠한다;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.progression.domain.policy.XpSource;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 이번 주 미스터리 지역(8단계): 그 주(처리 시각 기준)에 그 주의 미스터리 지역을 칠하면 보너스 50 — 주마다 한 번, 취소해도 회수 없음.
 * 어느 지역이 그 주의 미스터리인지는 카탈로그가 고른 기록이고(전체 사용자 공통), 기록이 없는 주는 보너스가 없다(소급 없음).
 */
@DisplayName("이번 주 미스터리 지역")
class MysteryBonusTest {

    private static final String 이번주 = "2026-09-28";
    private static final String 다음주 = "2026-10-05";

    @Nested
    @DisplayName("그 주의 미스터리 지역을 칠하면")
    class Found {

        @Test
        @DisplayName("보너스 50을 받는다")
        void bonus() {
            ExplorerProgress progress = 새_진행();

            ProgressChange change = 칠한다(progress, 방문(가평군).그주의미스터리(이번주, 가평군));

            assertThat(change.xpDelta()).isEqualTo(20 + 15 + 10 + 50);
            assertThat(progress.ledger().has(RefIds.mystery(progress.explorerId(), 이번주))).isTrue();
        }

        @Test
        @DisplayName("받은 주와 지역을 결과로 알린다")
        void announced() {
            ProgressChange change = 칠한다(새_진행(), 방문(가평군).그주의미스터리(이번주, 가평군));

            assertThat(change.mysteryFound()).hasValueSatisfying(found -> {
                assertThat(found.weekId()).isEqualTo(이번주);
                assertThat(found.region()).isEqualTo(가평군);
            });
        }

        @Test
        @DisplayName("처음 받으면 미스터리 탐험가 뱃지를 얻는다")
        void firstBadge() {
            ProgressChange change = 칠한다(새_진행(), 방문(가평군).그주의미스터리(이번주, 가평군));

            assertThat(change.badgesEarned()).contains("mystery1").doesNotContain("mystery5");
        }

        @Test
        @DisplayName("받은 주가 다섯이 되면 다음 단계 뱃지를 얻는다")
        void fifthBadge() {
            ExplorerProgress progress = 새_진행();
            for (int week = 0; week < 5; week++) {
                String weekId = LocalDate.parse(이번주).plusWeeks(week).toString();
                칠한다(progress, 방문(가평군).지도("map-" + week).처리시각(초(week)).그주의미스터리(weekId, 가평군));
            }

            assertThat(progress.mysteryFoundCount()).isEqualTo(5);
            assertThat(progress.badges()).containsKey("mystery5");
        }
    }

    @Nested
    @DisplayName("주마다 한 번이라")
    class OncePerWeek {

        @Test
        @DisplayName("같은 주에 다른 지도에서 또 칠해도 다시 받지 않는다")
        void sameWeekOtherMap() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(가평군).그주의미스터리(이번주, 가평군));

            ProgressChange again = 칠한다(progress, 방문(가평군).지도(다른지도).처리시각(초(1)).그주의미스터리(이번주, 가평군));

            assertThat(again.mysteryFound()).isEmpty();
            assertThat(progress.ledger().entriesOf(XpSource.MYSTERY_BONUS)).hasSize(1);
        }

        @Test
        @DisplayName("다음 주에는 그 주의 미스터리 지역으로 다시 받는다")
        void nextWeekAgain() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(가평군).그주의미스터리(이번주, 가평군));

            ProgressChange next = 칠한다(progress, 방문(종로구).처리시각(초(1)).그주의미스터리(다음주, 종로구));

            assertThat(next.mysteryFound()).isPresent();
            assertThat(progress.mysteryFoundCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("보너스가 없는 때")
    class NoBonus {

        @Test
        @DisplayName("그 주의 미스터리 지역이 아닌 곳을 칠하면 없다")
        void otherRegion() {
            ProgressChange change = 칠한다(새_진행(), 방문(종로구).그주의미스터리(이번주, 가평군));

            assertThat(change.mysteryFound()).isEmpty();
            assertThat(change.xpDelta()).isEqualTo(10 + 15 + 10);
        }

        @Test
        @DisplayName("그 주의 미스터리 기록이 없으면 없다")
        void noRecord() {
            ProgressChange change = 칠한다(새_진행(), 방문(가평군));

            assertThat(change.mysteryFound()).isEmpty();
        }
    }

    @Test
    @DisplayName("칠한 곳을 취소해도 받은 보너스는 되돌리지 않는다")
    void notRevokedOnCancel() {
        ExplorerProgress progress = 새_진행();
        칠한다(progress, 방문(가평군).그주의미스터리(이번주, 가평군));

        취소한다(progress, 방문(가평군).처리시각(초(1)));

        assertThat(progress.ledger().has(RefIds.mystery(progress.explorerId(), 이번주))).isTrue();
        assertThat(progress.mysteryFoundAt(이번주)).isPresent();
    }
}
