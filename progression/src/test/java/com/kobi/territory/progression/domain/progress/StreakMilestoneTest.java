package com.kobi.territory.progression.domain.progress;

import static com.kobi.territory.progression.domain.Fixtures.그달에_칠한다;
import static com.kobi.territory.progression.domain.Fixtures.그달에_한곳더_칠한다;
import static com.kobi.territory.progression.domain.Fixtures.새_진행;
import static com.kobi.territory.progression.domain.Fixtures.월간퀘스트를_모두_받는다;
import static com.kobi.territory.progression.domain.Fixtures.진행규칙;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.progression.domain.policy.XpSource;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 연속 탐험 마일스톤(8단계): 3·6·12·24개월에 처음 닿으면 한 번 — XP 50·100·200·400, 칭호(streak-3 …), 보호권 하나(보유 상한 안).
 * 끊겼다 다시 쌓아도 이미 받은 마일스톤은 다시 없다.
 */
@DisplayName("연속 탐험 마일스톤")
class StreakMilestoneTest {

    /** 7·8·9월 연속으로 칠한 탐험가(9월에 3개월). */
    private static ExplorerProgress 석달_연속() {
        ExplorerProgress progress = 새_진행();
        그달에_칠한다(progress, 2026, 7);
        그달에_칠한다(progress, 2026, 8);
        그달에_칠한다(progress, 2026, 9);
        return progress;
    }

    @Nested
    @DisplayName("3개월 연속에 처음 닿으면")
    class FirstReach {

        @Test
        @DisplayName("마일스톤 XP 50을 받는다")
        void xp() {
            ExplorerProgress progress = 새_진행();
            그달에_칠한다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 8);

            ProgressChange change = 그달에_칠한다(progress, 2026, 9);

            assertThat(change.milestonesReached()).containsExactly(3);
            assertThat(progress.ledger().entriesOf(XpSource.STREAK_MILESTONE)).extracting(XpLedgerEntry::amount)
                .containsExactly(50);
        }

        @Test
        @DisplayName("그 마일스톤의 칭호를 얻는다")
        void title() {
            assertThat(석달_연속().titles()).containsKey("streak-3");
        }

        @Test
        @DisplayName("보호권 하나를 받는다")
        void freeze() {
            assertThat(석달_연속().freezes().held()).isEqualTo(1);
        }

        @Test
        @DisplayName("보호권이 이미 가득이면 마일스톤 보호권은 받지 못한다")
        void freezeCapped() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 5);
            월간퀘스트를_모두_받는다(progress, 2026, 6);
            그달에_칠한다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 8);

            그달에_칠한다(progress, 2026, 9);

            assertThat(progress.freezes().held()).isEqualTo(2);
        }

        @Test
        @DisplayName("보호권으로 이은 연속도 마일스톤 개월로 센다")
        void bridgedStreakCounts() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 6);
            그달에_칠한다(progress, 2026, 6);
            그달에_칠한다(progress, 2026, 7);

            ProgressChange change = 그달에_칠한다(progress, 2026, 9); // 8월을 보호권으로 메워 3개월

            assertThat(change.milestonesReached()).containsExactly(3);
        }
    }

    @Nested
    @DisplayName("이미 받은 마일스톤은")
    class AlreadyReached {

        @Test
        @DisplayName("같은 달에 또 칠해도 다시 받지 않는다")
        void sameMonthAgain() {
            ExplorerProgress progress = 석달_연속();

            ProgressChange again = 그달에_한곳더_칠한다(progress, 2026, 9);

            assertThat(again.milestonesReached()).isEmpty();
        }

        @Test
        @DisplayName("끊겼다가 다시 3개월이 돼도 다시 받지 않는다")
        void notAgainAfterBreak() {
            ExplorerProgress progress = 석달_연속();
            그달에_칠한다(progress, 2027, 1); // 10~12월이 비어 끊김(보호권 하나로는 모자람)
            그달에_칠한다(progress, 2027, 2);

            ProgressChange change = 그달에_칠한다(progress, 2027, 3);

            assertThat(progress.streak().months()).isEqualTo(3);
            assertThat(change.milestonesReached()).isEmpty();
            assertThat(progress.ledger().entriesOf(XpSource.STREAK_MILESTONE)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("마일스톤 현황은")
    class Status {

        @Test
        @DisplayName("받은 단계와 다음 단계까지 더 필요한 개월 수를 알려 준다")
        void remaining() {
            ExplorerProgress progress = 석달_연속();

            assertThat(progress.milestoneStatus(진행규칙, YearMonth.of(2026, 9)))
                .extracting(status -> status.months() + ":" + status.reached() + ":" + status.remainingMonths())
                .containsExactly("3:true:0", "6:false:3", "12:false:9", "24:false:21");
        }

        @Test
        @DisplayName("받은 마일스톤과 받은 시각을 개월 수 순으로 남긴다")
        void reachedRecord() {
            assertThat(석달_연속().milestonesReached()).containsOnlyKeys(3);
        }
    }

    @Nested
    @DisplayName("다음 마일스톤은")
    class Next {

        @Test
        @DisplayName("아직 받지 않은 가장 가까운 단계이고 그 칭호를 알려 준다")
        void nearestUnreached() {
            assertThat(석달_연속().nextMilestone(진행규칙, YearMonth.of(2026, 9))).hasValueSatisfying(next -> {
                assertThat(next.months()).isEqualTo(6);
                assertThat(next.titleId()).isNull(); // 미니 칭호 규칙에는 streak-3 만 있다
                assertThat(next.remainingMonths()).isEqualTo(3);
            });
            assertThat(새_진행().nextMilestone(진행규칙, YearMonth.of(2026, 9)))
                .hasValueSatisfying(next -> assertThat(next.titleId()).isEqualTo("streak-3"));
        }

        @Test
        @DisplayName("모든 단계를 받으면 없다")
        void noneAfterAll() {
            ExplorerProgress progress = 새_진행();
            YearMonth month = YearMonth.of(2025, 1);
            for (int count = 0; count < 24; count++, month = month.plusMonths(1)) {
                그달에_칠한다(progress, month.getYear(), month.getMonthValue());
            }

            assertThat(progress.nextMilestone(진행규칙, month.minusMonths(1))).isEmpty();
        }
    }
}
