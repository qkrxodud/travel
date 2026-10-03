package com.kobi.territory.progression.domain.progress;

import static com.kobi.territory.progression.domain.Fixtures.그달;
import static com.kobi.territory.progression.domain.Fixtures.그달에_칠한다;
import static com.kobi.territory.progression.domain.Fixtures.그달에_한곳더_칠한다;
import static com.kobi.territory.progression.domain.Fixtures.새_진행;
import static com.kobi.territory.progression.domain.Fixtures.월간퀘스트를_모두_받는다;
import static com.kobi.territory.progression.domain.Fixtures.진행규칙;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.progression.domain.quest.QuestPeriod;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 보호권(8단계): 빈 달을 메워 연속 탐험을 지켜 준다. 한 달 월간 퀘스트를 모두 보상 받거나 마일스톤에 닿으면 받고, 최대 두 개까지 가진다.
 * 미니 규칙: 보호권 최대 2개, 월간 퀘스트 완주 1개, 3개월 마일스톤 보호권 1개.
 */
@DisplayName("보호권")
class StreakFreezeTest {

    private static final YearMonth 시월 = YearMonth.of(2026, 10);

    @Nested
    @DisplayName("한 달의 월간 퀘스트를 모두 보상 받으면")
    class FromMonthlyQuests {

        @Test
        @DisplayName("보호권 하나를 받는다")
        void earnsOne() {
            ExplorerProgress progress = 새_진행();

            월간퀘스트를_모두_받는다(progress, 2026, 7);

            assertThat(progress.freezes().held()).isEqualTo(1);
        }

        @Test
        @DisplayName("일부만 받았으면 아직 받지 않는다")
        void notUntilAll() {
            ExplorerProgress progress = 새_진행();
            QuestPeriod 칠월 = QuestPeriod.of(YearMonth.of(2026, 7));

            progress.applyQuestReward(칠월, "m3", 60, 그달(2026, 7), 진행규칙);
            progress.applyQuestReward(칠월, "mgun", 40, 그달(2026, 7).plusSeconds(1), 진행규칙);

            assertThat(progress.freezes().held()).isZero();
        }

        @Test
        @DisplayName("같은 달 보상 소식이 다시 와도 한 번만 받는다")
        void oncePerMonth() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 7);

            progress.applyQuestReward(QuestPeriod.of(YearMonth.of(2026, 7)), "mset", 10, 그달(2026, 7).plusSeconds(9), 진행규칙);

            assertThat(progress.freezes().held()).isEqualTo(1);
        }

        @Test
        @DisplayName("상시 도전 보상으로는 받지 않는다")
        void notFromAlwaysBoard() {
            ExplorerProgress progress = 새_진행();

            progress.applyQuestReward(QuestPeriod.ALL, "leg5", 150, 그달(2026, 7), 진행규칙);

            assertThat(progress.freezes().held()).isZero();
        }
    }

    @Nested
    @DisplayName("가질 수 있는 보호권은")
    class Cap {

        @Test
        @DisplayName("최대 두 개까지다")
        void atMostTwo() {
            ExplorerProgress progress = 새_진행();

            월간퀘스트를_모두_받는다(progress, 2026, 5);
            월간퀘스트를_모두_받는다(progress, 2026, 6);
            월간퀘스트를_모두_받는다(progress, 2026, 7);

            assertThat(progress.freezes().held()).isEqualTo(2);
        }

        @Test
        @DisplayName("가득 찼을 때 받을 일은 나중에 자리가 나도 다시 주지 않는다")
        void forfeitedStaysForfeited() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 5);
            월간퀘스트를_모두_받는다(progress, 2026, 6);
            월간퀘스트를_모두_받는다(progress, 2026, 7); // 가득 차 못 받음
            그달에_칠한다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 9); // 8월을 메우느라 하나 씀

            progress.applyQuestReward(QuestPeriod.of(YearMonth.of(2026, 7)), "m3", 60, 그달(2026, 9), 진행규칙);

            assertThat(progress.freezes().held()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("빈 달이 생긴 뒤 다시 칠하면")
    class Gap {

        @Test
        @DisplayName("빈 달 수만큼 보호권이 있으면 그만큼 써서 연속을 잇는다")
        void bridgesGap() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 8);

            ProgressChange change = 그달에_칠한다(progress, 2026, 10); // 9월이 빔

            assertThat(change.freezesUsed()).isEqualTo(1);
            assertThat(progress.freezes().held()).isZero();
            assertThat(progress.streak()).isEqualTo(Streak.of(2, 시월));
        }

        @Test
        @DisplayName("빈 달은 연속 개월에 더하지 않는다")
        void gapNotCounted() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 6);
            월간퀘스트를_모두_받는다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 7);

            그달에_칠한다(progress, 2026, 10); // 8·9월이 빔 — 보호권 둘

            assertThat(progress.streak().months()).isEqualTo(2);
        }

        @Test
        @DisplayName("보호권이 모자라면 끊기고 보호권은 쓰지 않는다")
        void notEnough() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 7);

            ProgressChange change = 그달에_칠한다(progress, 2026, 10); // 8·9월이 빔 — 보호권 하나뿐

            assertThat(change.freezesUsed()).isZero();
            assertThat(progress.freezes().held()).isEqualTo(1);
            assertThat(progress.streak()).isEqualTo(Streak.of(1, 시월));
        }

        @Test
        @DisplayName("바로 다음 달이면 보호권을 쓰지 않는다")
        void nextMonthNoUse() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 7);

            그달에_칠한다(progress, 2026, 8);

            assertThat(progress.freezes().held()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 달에 또 칠해도 보호권을 다시 쓰지 않는다")
        void onlyFirstCheckInOfMonth() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 6);
            월간퀘스트를_모두_받는다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 9);

            ProgressChange again = 그달에_한곳더_칠한다(progress, 2026, 9);

            assertThat(again.freezesUsed()).isZero();
            assertThat(progress.freezes().held()).isEqualTo(1);
        }

        @Test
        @DisplayName("쓴 기록에는 이은 달과 쓴 개수가 남는다")
        void useRecorded() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 7);

            그달에_칠한다(progress, 2026, 9);

            assertThat(progress.freezes().lastUse()).hasValueSatisfying(use -> {
                assertThat(use.month()).isEqualTo(YearMonth.of(2026, 9));
                assertThat(use.amount()).isEqualTo(-1);
            });
        }
    }

    @Nested
    @DisplayName("이번 달에 보이는 연속은")
    class Display {

        @Test
        @DisplayName("빈 달을 가진 보호권으로 메울 수 있으면 아직 이어진 것으로 보인다")
        void keptByFreezes() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 8);

            assertThat(progress.streakMonthsAsOf(시월)).isEqualTo(2); // 9월이 비었지만 보호권 하나
        }

        @Test
        @DisplayName("메울 보호권이 모자라면 0으로 보인다")
        void brokenWithoutFreezes() {
            ExplorerProgress progress = 새_진행();
            그달에_칠한다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 8);

            assertThat(progress.streakMonthsAsOf(시월)).isZero();
        }

        @Test
        @DisplayName("이번 달에 칠하면 쓰게 될 보호권 수를 알 수 있다")
        void freezesNeeded() {
            ExplorerProgress progress = 새_진행();
            그달에_칠한다(progress, 2026, 7);

            assertThat(progress.streak().emptyMonthsBefore(시월)).isEqualTo(2);
            assertThat(progress.streak().emptyMonthsBefore(YearMonth.of(2026, 8))).isZero();
        }
    }
    @Nested
    @DisplayName("지금 연속 구간에서 보호권으로 메운 달은")
    class FrozenMonths {

        @Test
        @DisplayName("여러 번 메웠어도 메운 달을 모두 알려 준다")
        void allBridgedMonths() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 5);
            월간퀘스트를_모두_받는다(progress, 2026, 6);
            그달에_칠한다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 9); // 8월을 하나로 메움
            월간퀘스트를_모두_받는다(progress, 2026, 9);

            그달에_칠한다(progress, 2026, 12); // 10·11월을 둘로 메움

            assertThat(progress.frozenMonthsAsOf(YearMonth.of(2026, 12)))
                .containsExactly(YearMonth.of(2026, 8), YearMonth.of(2026, 10), YearMonth.of(2026, 11));
        }

        @Test
        @DisplayName("끊기기 전 구간에서 메운 달은 들어가지 않는다")
        void onlyCurrentRun() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 6);
            그달에_칠한다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 9); // 8월을 메움
            그달에_칠한다(progress, 2027, 1); // 10~12월이 비고 보호권이 없어 끊김

            그달에_칠한다(progress, 2027, 2);

            assertThat(progress.frozenMonthsAsOf(YearMonth.of(2027, 2))).isEmpty();
        }

        @Test
        @DisplayName("연속이 끊긴 것으로 보이면 하나도 없다")
        void noneWhenBroken() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 6);
            그달에_칠한다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 9); // 8월을 메움

            assertThat(progress.frozenMonthsAsOf(YearMonth.of(2027, 3))).isEmpty();
        }

        @Test
        @DisplayName("이번 달에 칠하면 메우게 될 빈 달은 아직 들어가지 않는다")
        void notYetUsed() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 8);

            assertThat(progress.streakMonthsAsOf(시월)).isEqualTo(2);
            assertThat(progress.frozenMonthsAsOf(시월)).isEmpty();
        }
    }

    @Nested
    @DisplayName("이번 달 보호권 진행은")
    class ThisMonth {

        @Test
        @DisplayName("받은 월간 보상 수와 모두 받아야 하는 수, 받으면 주는 보호권 수를 알려 준다")
        void counts() {
            ExplorerProgress progress = 새_진행();
            progress.applyQuestReward(QuestPeriod.of(시월), "m3", 60, 그달(2026, 10), 진행규칙);

            MonthlyFreezeProgress thisMonth = progress.monthlyFreezeProgress(진행규칙, 시월);

            assertThat(thisMonth.questsRewarded()).isEqualTo(1);
            assertThat(thisMonth.questsRequired()).isEqualTo(4);
            assertThat(thisMonth.reward()).isEqualTo(1);
            assertThat(thisMonth.earned()).isFalse();
        }

        @Test
        @DisplayName("모두 받으면 이번 달 몫을 채웠고 실제로 하나 늘었다")
        void earned() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 10);

            MonthlyFreezeProgress thisMonth = progress.monthlyFreezeProgress(진행규칙, 시월);

            assertThat(thisMonth.earned()).isTrue();
            assertThat(thisMonth.granted()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 가득이면 몫은 채웠지만 늘어난 보호권은 없다")
        void cappedMonth() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 8);
            월간퀘스트를_모두_받는다(progress, 2026, 9);
            월간퀘스트를_모두_받는다(progress, 2026, 10);

            MonthlyFreezeProgress thisMonth = progress.monthlyFreezeProgress(진행규칙, 시월);

            assertThat(thisMonth.earned()).isTrue();
            assertThat(thisMonth.granted()).isZero();
        }
    }
}
