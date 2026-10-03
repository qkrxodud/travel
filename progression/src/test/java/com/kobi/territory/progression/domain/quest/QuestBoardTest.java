package com.kobi.territory.progression.domain.quest;

import static com.kobi.territory.progression.domain.Fixtures.가평군;
import static com.kobi.territory.progression.domain.Fixtures.기준시각;
import static com.kobi.territory.progression.domain.Fixtures.나;
import static com.kobi.territory.progression.domain.Fixtures.서울시각;
import static com.kobi.territory.progression.domain.Fixtures.울릉군;
import static com.kobi.territory.progression.domain.Fixtures.종로구;
import static com.kobi.territory.progression.domain.Fixtures.중구;
import static com.kobi.territory.progression.domain.Fixtures.퀘스트;
import static com.kobi.territory.progression.domain.Fixtures.퀘스트사실;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 월간 퀘스트·상시 도전 보드. 월간: m3 새 지역 3곳, mgun 일반 아닌 지역 1곳, mprov 처음 가는 시·도 1곳, mset 테마 지역 2곳.
 * 상시: leg5 전설 5곳, p3 두 곳 이상 칠한 시·도 2개.
 */
@DisplayName("퀘스트 보드")
class QuestBoardTest {

    private static final YearMonth 구월 = YearMonth.of(2026, 9);
    private static final YearMonth 시월 = YearMonth.of(2026, 10);

    private static String code(Throwable thrown) {
        return ((TerritoryException) thrown).code();
    }

    private static QuestBoard 시월보드(RegionCode... firstInProvinceRegions) {
        QuestBoard board = QuestBoard.empty(나, QuestPeriod.of(시월));
        for (RegionCode region : firstInProvinceRegions) board.applyVisit(퀘스트사실(region, true), 퀘스트, 시월);
        return board;
    }

    private static int current(QuestBoard board, String questId) {
        return board.of(questId).current(퀘스트.require(questId));
    }

    @Nested
    @DisplayName("내가 칠한 지역은 지표마다 센다")
    class Tally {

        @Test
        @DisplayName("새로 칠한 지역 수를 센다")
        void newRegions() {
            assertThat(current(시월보드(종로구, 가평군), "m3")).isEqualTo(2);
        }

        @Test
        @DisplayName("같은 지역은 다시 와도 한 번만 센다")
        void sameRegionOnce() {
            QuestBoard board = 시월보드(종로구);

            board.applyVisit(퀘스트사실(종로구, true), 퀘스트, 시월);

            assertThat(current(board, "m3")).isEqualTo(1);
        }

        @Test
        @DisplayName("일반이 아닌 지역을 센다")
        void nonCommon() {
            assertThat(시월보드(가평군).of("mgun").achieved(퀘스트.require("mgun"))).isTrue();
        }

        @Test
        @DisplayName("처음 가는 시·도를 센다")
        void firstInProvince() {
            assertThat(current(시월보드(종로구), "mprov")).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 가 본 시·도의 지역은 처음 가는 시·도로 세지 않는다")
        void notFirstInProvince() {
            QuestBoard board = 시월보드();

            board.applyVisit(퀘스트사실(중구, false), 퀘스트, 시월);

            assertThat(current(board, "mprov")).isZero();
        }

        @Test
        @DisplayName("테마에 든 지역을 센다")
        void themeRegions() {
            assertThat(current(시월보드(종로구, 가평군), "mset")).isEqualTo(2);
        }

        @Test
        @DisplayName("월간 보드에는 상시 도전이 없다")
        void noAlwaysQuestOnMonthlyBoard() {
            assertThat(current(시월보드(울릉군), "leg5")).isZero();
        }

        @Test
        @DisplayName("상시 도전 보드는 전설 지역을 센다")
        void alwaysBoardCountsLegends() {
            QuestBoard always = QuestBoard.empty(나, QuestPeriod.ALL);

            always.applyVisit(퀘스트사실(울릉군, true), 퀘스트, 시월);

            assertThat(current(always, "leg5")).isEqualTo(1);
        }

        @Test
        @DisplayName("목표에 닿으면 더 세지 않는다")
        void capsAtTarget() {
            QuestBoard board = 시월보드(종로구, 가평군);
            board.applyVisit(퀘스트사실(중구, false), 퀘스트, 시월);

            board.applyVisit(퀘스트사실(울릉군, true), 퀘스트, 시월);

            assertThat(current(board, "m3")).isEqualTo(3);
            assertThat(board.of("m3").tally().size()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("시·도 수를 세는 도전은")
    class ProvincesWithMinRegions {

        private QuestBoard 상시보드(RegionCode... regions) {
            QuestBoard always = QuestBoard.empty(나, QuestPeriod.ALL);
            for (RegionCode region : regions) always.applyVisit(퀘스트사실(region, false), 퀘스트, 시월);
            return always;
        }

        @Test
        @DisplayName("한 시·도에서 기준 수만큼 칠해야 그 시·도를 하나로 센다")
        void provinceCountsAtMinimum() {
            assertThat(current(상시보드(종로구), "p3")).isZero();
            assertThat(current(상시보드(종로구, 중구), "p3")).isEqualTo(1);
        }

        @Test
        @DisplayName("한 시·도에서 기준 수를 넘게 칠해도 더 세지 않는다")
        void extraRegionsNotCounted() {
            QuestBoard always = 상시보드(종로구, 중구, RegionCode.of("KR-11030"));

            assertThat(current(always, "p3")).isEqualTo(1);
            assertThat(always.of("p3").tally().size()).isEqualTo(2);
        }

        @Test
        @DisplayName("기준을 채운 시·도가 목표 수에 닿으면 달성한다")
        void achieved() {
            QuestBoard always = 상시보드(종로구, 중구, 가평군, RegionCode.of("KR-31380"));

            assertThat(always.of("p3").achieved(퀘스트.require("p3"))).isTrue();
        }
    }

    @Nested
    @DisplayName("보상을 받을 때")
    class Claim {

        @Test
        @DisplayName("달성한 퀘스트는 그 보드의 보상 XP를 받는다")
        void achievedQuestPays() {
            QuestReward reward = 시월보드(가평군).claim("mgun", 퀘스트, 기준시각, 시월);

            assertThat(reward.xp()).isEqualTo(40);
            assertThat(reward.period()).isEqualTo(QuestPeriod.of(시월));
        }

        @Test
        @DisplayName("받은 퀘스트는 받은 것으로 남아 다시 받을 수 있는 상태가 아니다")
        void claimedState() {
            QuestBoard board = 시월보드(가평군);

            board.claim("mgun", 퀘스트, 기준시각, 시월);

            assertThat(board.of("mgun").claimed()).isTrue();
            assertThat(board.of("mgun").claimable(퀘스트.require("mgun"))).isFalse();
        }

        @Test
        @DisplayName("달성하기 전에는 받을 수 없다")
        void notAchieved() {
            assertThatThrownBy(() -> 시월보드().claim("mgun", 퀘스트, 기준시각, 시월))
                .satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_NOT_COMPLETED"));
        }

        @Test
        @DisplayName("한 번 받은 보상은 다시 받을 수 없다")
        void onlyOnce() {
            QuestBoard board = 시월보드(가평군);
            board.claim("mgun", 퀘스트, 기준시각, 시월);

            assertThatThrownBy(() -> board.claim("mgun", 퀘스트, 기준시각, 시월))
                .satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_ALREADY_CLAIMED"));
        }

        @Test
        @DisplayName("없는 퀘스트는 받을 수 없다")
        void unknownQuest() {
            assertThatThrownBy(() -> 시월보드(가평군).claim("nope", 퀘스트, 기준시각, 시월))
                .satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_NOT_FOUND"));
        }

        @Test
        @DisplayName("월간 보드에서 상시 도전 보상을 받을 수 없다")
        void alwaysQuestNotOnMonthlyBoard() {
            assertThatThrownBy(() -> 시월보드(가평군).claim("leg5", 퀘스트, 기준시각, 시월))
                .satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("달이 지나면")
    class MonthClosed {

        private QuestBoard 구월보드() {
            QuestBoard board = QuestBoard.empty(나, QuestPeriod.of(구월));
            board.applyVisit(퀘스트사실(가평군, true), 퀘스트, 구월);
            return board;
        }

        @Test
        @DisplayName("지난 달 보드는 더 세지 않는다")
        void closedBoardDoesNotCount() {
            QuestBoard board = 구월보드();

            board.applyVisit(퀘스트사실(종로구, true), 퀘스트, 시월); // 10월에 처리된 체크인

            assertThat(board.of("m3").tally().size()).isEqualTo(1);
        }

        @Test
        @DisplayName("지난 달 보드의 보상은 받을 수 없다")
        void closedBoardCannotClaim() {
            assertThatThrownBy(() -> 구월보드().claim("mgun", 퀘스트, 기준시각, 시월))
                .satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_BOARD_CLOSED"));
        }

        @Test
        @DisplayName("상시 도전 보드는 닫히지 않는다")
        void alwaysBoardNeverCloses() {
            assertThat(QuestPeriod.ALL.closedAt(시월.plusYears(5))).isFalse();
        }

        @Test
        @DisplayName("체크인이 어느 달 보드에 들어갈지는 서울 시각의 처리 시각으로 정한다")
        void monthBySeoulProcessingTime() {
            Instant 시월말밤 = Instant.parse("2026-10-31T14:30:00Z");   // 10-31 23:30 KST
            Instant 십일월첫새벽 = Instant.parse("2026-10-31T15:30:00Z"); // 11-01 00:30 KST

            assertThat(QuestPeriod.monthOf(시월말밤, 서울시각)).isEqualTo(QuestPeriod.of(시월));
            assertThat(QuestPeriod.monthOf(십일월첫새벽, 서울시각)).isEqualTo(QuestPeriod.of(YearMonth.of(2026, 11)));
        }
    }

    @Nested
    @DisplayName("처음 가는 시·도인지는")
    class FirstInProvinceFact {

        @Test
        @DisplayName("그 시·도를 전에 밟은 적이 없으면 처음이다")
        void neverVisited() {
            assertThat(QuestFact.of(종로구, "KR-11", Rarity.COMMON, true, false).firstInProvince()).isTrue();
        }

        @Test
        @DisplayName("그 시·도를 전에 밟은 적이 있으면 처음이 아니다")
        void visitedBefore() {
            assertThat(QuestFact.of(종로구, "KR-11", Rarity.COMMON, true, true).firstInProvince()).isFalse();
        }
    }
}
