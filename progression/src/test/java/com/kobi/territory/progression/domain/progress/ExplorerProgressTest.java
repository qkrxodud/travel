package com.kobi.territory.progression.domain.progress;

import static com.kobi.territory.progression.domain.Fixtures.가평군;
import static com.kobi.territory.progression.domain.Fixtures.기준시각;
import static com.kobi.territory.progression.domain.Fixtures.나;
import static com.kobi.territory.progression.domain.Fixtures.다른지도;
import static com.kobi.territory.progression.domain.Fixtures.방문;
import static com.kobi.territory.progression.domain.Fixtures.새_진행;
import static com.kobi.territory.progression.domain.Fixtures.울릉군;
import static com.kobi.territory.progression.domain.Fixtures.종로구;
import static com.kobi.territory.progression.domain.Fixtures.중구;
import static com.kobi.territory.progression.domain.Fixtures.지도;
import static com.kobi.territory.progression.domain.Fixtures.진행규칙;
import static com.kobi.territory.progression.domain.Fixtures.초;
import static com.kobi.territory.progression.domain.Fixtures.취소한다;
import static com.kobi.territory.progression.domain.Fixtures.칠한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.progression.domain.policy.XpSource;
import com.kobi.territory.progression.domain.quest.QuestPeriod;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 탐험가 진행(XP 장부·레벨·스트릭·뱃지·칭호·탐험가 단위 지역). 보상 액수: 기본 10/20/50, 시·도 첫 발 15, 선점 10, 테마 100.
 * 회귀 출처: 2단계 D2·D3·D4(여러 지도·시·도 첫 발·기본 XP 회차), 3단계 결정 6·Q3(방문 회차), QA P3-3·P3-4.
 */
@DisplayName("탐험가 진행")
class ExplorerProgressTest {

    private static String code(Throwable thrown) {
        return ((TerritoryException) thrown).code();
    }

    @Nested
    @DisplayName("처음 시작하면")
    class Start {

        @Test
        @DisplayName("XP 0, 레벨 1에서 시작한다")
        void zeroXpLevelOne() {
            ExplorerProgress progress = 새_진행();

            assertThat(progress.xp()).isZero();
            assertThat(progress.level()).isEqualTo(1);
        }

        @Test
        @DisplayName("레벨 1 칭호를 얻고 그 칭호가 보인다")
        void levelOneTitle() {
            ExplorerProgress progress = 새_진행();

            assertThat(progress.titles()).containsKey("lv1");
            assertThat(progress.displayTitle(진행규칙)).isEqualTo("lv1");
        }
    }

    @Nested
    @DisplayName("내가 지역을 칠하면")
    class Paint {

        @Test
        @DisplayName("처음 칠한 곳은 기본 XP, 시·도 첫 발 보너스, 선점 보너스를 받는다")
        void firstPaintRewards() {
            ExplorerProgress progress = 새_진행();

            assertThat(칠한다(progress, 방문(종로구)).xpDelta()).isEqualTo(10 + 15 + 10);
        }

        @Test
        @DisplayName("같은 시·도의 두 번째 지역은 시·도 첫 발 보너스가 없다")
        void secondInProvince() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));

            assertThat(칠한다(progress, 방문(중구).처리시각(초(1))).xpDelta()).isEqualTo(10 + 10);
        }

        @Test
        @DisplayName("지도에서 먼저 칠한 사람이 아니면 선점 보너스가 없다")
        void notFirstOnMap() {
            ExplorerProgress progress = 새_진행();

            assertThat(칠한다(progress, 방문(종로구).선점아님()).xpDelta()).isEqualTo(10 + 15);
        }

        @Test
        @DisplayName("XP는 언제나 장부에 쌓인 금액의 합이다")
        void xpIsLedgerSum() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            칠한다(progress, 방문(중구).처리시각(초(1)));

            assertThat(progress.xp()).isEqualTo(55).isEqualTo(progress.ledger().total());
            assertThat(progress.ledger().entries().stream().mapToInt(XpLedgerEntry::amount).sum()).isEqualTo(55);
        }

        @Test
        @DisplayName("레벨 하한을 넘는 순간 레벨이 오른다")
        void levelUp() {
            ExplorerProgress progress = 새_진행();

            assertThat(칠한다(progress, 방문(종로구)).levelUp()).isEmpty();          // 35 < 40
            assertThat(칠한다(progress, 방문(중구).처리시각(초(1))).levelUp()).contains(2); // 55 ≥ 40
        }

        @Test
        @DisplayName("같은 체크인 소식이 두 번 와도 한 번만 반영된다")
        void sameVisitTwice() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(가평군));

            assertThat(칠한다(progress, 방문(가평군)).xpDelta()).isZero();
            assertThat(progress.xp()).isEqualTo(20 + 15 + 10);
            assertThat(progress.regions().find(가평군).orElseThrow().activeMapCount()).isEqualTo(1);
        }

        @Nested
        @DisplayName("같은 지역을 여러 지도에서 칠할 때")
        class SeveralMaps {

            @Test
            @DisplayName("기본 XP는 한 번만, 선점 보너스는 지도마다 받는다")
            void baseOnceClaimPerMap() {
                ExplorerProgress progress = 새_진행();
                칠한다(progress, 방문(종로구));

                assertThat(칠한다(progress, 방문(종로구).지도(다른지도).처리시각(초(1))).xpDelta()).isEqualTo(10);
                assertThat(progress.regions().find(종로구).orElseThrow().activeMapCount()).isEqualTo(2);
            }

            @Test
            @DisplayName("시·도 첫 발은 탐험가 기준이라 다른 지도의 같은 시·도에서는 다시 받지 않는다")
            void provinceFirstPerExplorer() {
                ExplorerProgress progress = 새_진행();
                칠한다(progress, 방문(종로구));

                assertThat(칠한다(progress, 방문(중구).지도(다른지도).처리시각(초(1))).xpDelta()).isEqualTo(10 + 10);
            }
        }
    }

    @Nested
    @DisplayName("칠한 곳을 취소하면")
    class Cancel {

        @Test
        @DisplayName("기본 XP만 되돌리고 시·도 첫 발과 선점 보너스는 남는다")
        void onlyBaseRevoked() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));

            assertThat(취소한다(progress, 방문(종로구).처리시각(초(1))).xpDelta()).isEqualTo(-10);
            assertThat(progress.xp()).isEqualTo(25);
        }

        @Test
        @DisplayName("같은 취소 소식이 다시 와도 더 되돌리지 않는다")
        void sameCancelTwice() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            취소한다(progress, 방문(종로구).처리시각(초(1)));

            assertThat(취소한다(progress, 방문(종로구).처리시각(초(2))).xpDelta()).isZero();
        }

        @Test
        @DisplayName("다른 지도에 아직 칠해져 있으면 기본 XP를 되돌리지 않는다")
        void stillOnAnotherMap() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            칠한다(progress, 방문(종로구).지도(다른지도).처리시각(초(1)));

            assertThat(취소한다(progress, 방문(종로구).처리시각(초(2))).xpDelta()).isZero();
            assertThat(progress.regions().find(종로구).orElseThrow().active()).isTrue();
        }

        @Test
        @DisplayName("모든 지도에서 사라지면 기본 XP를 되돌린다")
        void goneFromAllMaps() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            칠한다(progress, 방문(종로구).지도(다른지도).처리시각(초(1)));
            취소한다(progress, 방문(종로구).처리시각(초(2)));

            assertThat(취소한다(progress, 방문(종로구).지도(다른지도).처리시각(초(3))).xpDelta()).isEqualTo(-10);
            assertThat(progress.regions().find(종로구).orElseThrow().active()).isFalse();
        }

        @Test
        @DisplayName("다시 칠하면 기본 XP를 새로 받고 시·도 첫 발과 선점 보너스는 다시 받지 않는다")
        void repaintGetsBaseAgain() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            취소한다(progress, 방문(종로구).처리시각(초(1)));

            assertThat(칠한다(progress, 방문(종로구).처리시각(초(2))).xpDelta()).isEqualTo(10);
            assertThat(progress.ledger().has(RefIds.regionGrant(나, 종로구, 2))).isTrue();
            assertThat(progress.xp()).isEqualTo(35);
        }

        @Test
        @DisplayName("테마 완성과 퀘스트 보상 XP는 그대로 남는다")
        void themeAndQuestRewardsStay() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            progress.applyThemeCompleted("han", 초(1), 진행규칙);
            progress.applyQuestReward(QuestPeriod.of(YearMonth.of(2026, 10)), "m3", 60, 초(2), 진행규칙);

            취소한다(progress, 방문(종로구).처리시각(초(3)));

            assertThat(progress.xp()).isEqualTo(35 - 10 + 100 + 60);
            assertThat(progress.ledger().has(RefIds.theme(나, "han"))).isTrue();
        }

        @Test
        @DisplayName("XP가 레벨 하한 아래로 내려가면 레벨도 내려가지만 레벨업 소식은 없다")
        void levelGoesDownQuietly() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            칠한다(progress, 방문(중구).처리시각(초(1))); // 55 → 레벨 2
            취소한다(progress, 방문(중구).처리시각(초(2)));

            ProgressChange change = 취소한다(progress, 방문(종로구).처리시각(초(3))); // 35 → 레벨 1

            assertThat(progress.level()).isEqualTo(1);
            assertThat(change.levelUp()).isEmpty();
        }
    }

    @Nested
    @DisplayName("늦게 도착한 예전 회차 소식은")
    class Generations {

        @Test
        @DisplayName("취소 뒤에 다시 온 예전 회차 체크인을 무시한다")
        void staleCheckInAfterCancel() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구).회차(1).처리시각(초(1)));
            취소한다(progress, 방문(종로구).회차(1).처리시각(초(2)));
            long afterCancel = progress.xp();

            assertThat(칠한다(progress, 방문(종로구).회차(1).처리시각(초(1))).xpDelta()).isZero();
            assertThat(progress.xp()).isEqualTo(afterCancel);
            assertThat(progress.regions().find(종로구).orElseThrow().active()).isFalse();
        }

        @Test
        @DisplayName("다시 칠한 뒤에 온 예전 회차 취소를 무시한다")
        void staleCancelAfterRepaint() {
            ExplorerProgress progress = repaintedTwice();
            long xp = progress.xp();

            assertThat(취소한다(progress, 방문(종로구).회차(1).처리시각(초(4))).xpDelta()).isZero();
            assertThat(progress.xp()).isEqualTo(xp);
            assertThat(progress.regions().find(종로구).orElseThrow().active()).isTrue();
        }

        @Test
        @DisplayName("지금 회차의 취소는 반영한다")
        void currentCancelApplies() {
            ExplorerProgress progress = repaintedTwice();
            취소한다(progress, 방문(종로구).회차(1).처리시각(초(4)));

            assertThat(취소한다(progress, 방문(종로구).회차(2).처리시각(초(5))).xpDelta()).isEqualTo(-10);
        }

        @Test
        @DisplayName("회차를 모르는 예전 소식은 그 지도에 회차 기록이 있으면 무시한다")
        void unknownGenerationIgnoredWhenMarked() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구).회차(1).처리시각(초(1)));

            assertThat(취소한다(progress, 방문(종로구).회차(0).처리시각(초(2))).xpDelta()).isZero();
            assertThat(progress.regions().find(종로구).orElseThrow().active()).isTrue();
        }

        @Test
        @DisplayName("회차를 모르는 예전 소식은 회차 기록이 없는 지도에서는 반영한다")
        void unknownGenerationAppliesOnUnmarkedMap() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구).회차(1).처리시각(초(1)));

            assertThat(칠한다(progress, 방문(종로구).지도(다른지도).선점아님().회차(0).처리시각(초(3))).xpDelta()).isZero(); // 기본 XP 는 이미 활성
            assertThat(progress.regions().find(종로구).orElseThrow().activeMapCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("회차 기록이 하나도 없으면 회차를 모르는 소식을 그대로 반영한다")
        void unknownGenerationWithoutMarks() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구).회차(0).처리시각(초(1)));

            assertThat(취소한다(progress, 방문(종로구).회차(0).처리시각(초(2))).xpDelta()).isEqualTo(-10);
            assertThat(칠한다(progress, 방문(종로구).회차(0).처리시각(초(3))).xpDelta()).isEqualTo(10);
        }

        /** 1회차를 칠하고 취소한 뒤 2회차로 다시 칠한 진행. */
        private ExplorerProgress repaintedTwice() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구).회차(1).처리시각(초(1)));
            취소한다(progress, 방문(종로구).회차(1).처리시각(초(2)));
            칠한다(progress, 방문(종로구).회차(2).처리시각(초(3)));
            return progress;
        }
    }

    @Nested
    @DisplayName("지도의 선점이 나에게 넘어오면")
    class ClaimTransfer {

        @Test
        @DisplayName("새 선점자로서 선점 보너스를 받는다")
        void newClaimantBonus() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(가평군).선점아님().회차(1).처리시각(초(1)));

            assertThat(progress.applyClaimTransferred(지도, 가평군, Rarity.RARE, 초(2), 진행규칙).xpDelta()).isEqualTo(10);
            assertThat(progress.ledger().has(RefIds.claim(지도, 가평군, 나))).isTrue();
        }

        @Test
        @DisplayName("같은 이전 소식이 다시 와도 한 번만 받는다")
        void transferTwice() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(가평군).선점아님().회차(1).처리시각(초(1)));
            progress.applyClaimTransferred(지도, 가평군, Rarity.RARE, 초(2), 진행규칙);

            assertThat(progress.applyClaimTransferred(지도, 가평군, Rarity.RARE, 초(3), 진행규칙).xpDelta()).isZero();
        }

        @Test
        @DisplayName("그 지도 그 지역의 선점 보너스를 이미 받았으면 다시 받지 않는다")
        void alreadyClaimedOnThisMap() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(가평군)); // 내가 선점했었다

            assertThat(progress.applyClaimTransferred(지도, 가평군, Rarity.RARE, 초(2), 진행규칙).xpDelta()).isZero();
        }
    }

    @Nested
    @DisplayName("테마 완성 소식을 받으면")
    class ThemeCompleted {

        @Test
        @DisplayName("테마 완성 보너스 100을 받는다")
        void bonus() {
            assertThat(새_진행().applyThemeCompleted("han", 초(1), 진행규칙).xpDelta()).isEqualTo(100);
        }

        @Test
        @DisplayName("같은 테마의 보너스는 탐험가마다 한 번만 받는다")
        void oncePerTheme() {
            ExplorerProgress progress = 새_진행();
            progress.applyThemeCompleted("han", 초(1), 진행규칙);

            assertThat(progress.applyThemeCompleted("han", 초(2), 진행규칙).xpDelta()).isZero();
        }

        @Test
        @DisplayName("그 테마의 칭호와 테마 완성 뱃지를 얻는다")
        void titleAndBadge() {
            ProgressChange change = 새_진행().applyThemeCompleted("han", 초(1), 진행규칙);

            assertThat(change.titlesEarned()).contains("set-han");
            assertThat(change.badgesEarned()).contains("set1");
        }

        @Test
        @DisplayName("오른 레벨까지의 레벨 칭호만 얻는다")
        void levelTitleUpToNewLevel() {
            ProgressChange change = 새_진행().applyThemeCompleted("han", 초(1), 진행규칙);

            assertThat(change.levelUp()).contains(2);
            assertThat(change.titlesEarned()).doesNotContain("lv3"); // 100 < 120
        }
    }

    @Nested
    @DisplayName("퀘스트 보상을 받으면")
    class QuestReward {

        private static final QuestPeriod 시월 = QuestPeriod.of(YearMonth.of(2026, 10));

        @Test
        @DisplayName("보상 XP를 받는다")
        void rewardXp() {
            assertThat(새_진행().applyQuestReward(시월, "m3", 60, 초(1), 진행규칙).xpDelta()).isEqualTo(60);
        }

        @Test
        @DisplayName("같은 보드의 같은 퀘스트 보상은 한 번만 받는다")
        void oncePerBoard() {
            ExplorerProgress progress = 새_진행();
            progress.applyQuestReward(QuestPeriod.ALL, "leg5", 150, 초(1), 진행규칙);

            assertThat(progress.applyQuestReward(QuestPeriod.ALL, "leg5", 150, 초(2), 진행규칙).xpDelta()).isZero();
        }

        @Test
        @DisplayName("다른 달의 같은 월간 퀘스트는 따로 받는다")
        void eachMonthSeparately() {
            ExplorerProgress progress = 새_진행();
            progress.applyQuestReward(QuestPeriod.of(YearMonth.of(2026, 9)), "m3", 60, 초(1), 진행규칙);

            assertThat(progress.applyQuestReward(시월, "m3", 60, 초(2), 진행규칙).xpDelta()).isEqualTo(60);
        }

        @Test
        @DisplayName("상시 도전 보상은 그 도전의 칭호도 준다")
        void alwaysChallengeTitle() {
            ProgressChange change = 새_진행().applyQuestReward(QuestPeriod.ALL, "leg5", 150, 초(1), 진행규칙);

            assertThat(change.titlesEarned()).contains("long-leg5", "lv3");
        }
    }

    @Nested
    @DisplayName("뱃지와 칭호는")
    class BadgesAndTitles {

        @Test
        @DisplayName("한 시·도를 다 칠하면 그 시·도 뱃지와 '주인' 칭호를 얻는다")
        void provinceComplete() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));

            ProgressChange change = 칠한다(progress, 방문(중구).처리시각(초(1)));

            assertThat(change.badgesEarned()).contains("seoul");
            assertThat(change.titlesEarned()).contains("own-KR-11");
        }

        @Test
        @DisplayName("전체 지역의 절반을 넘게 칠하면 정복률 뱃지를 얻는다")
        void conquestRatio() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            칠한다(progress, 방문(중구).처리시각(초(1))); // 3곳 중 2곳 = 67%

            assertThat(progress.badges()).containsKeys("first", "half");
        }

        @Test
        @DisplayName("전설 지역을 칠하면 전설 뱃지를 얻는다")
        void legend() {
            ProgressChange change = 칠한다(새_진행(), 방문(울릉군));

            assertThat(change.badgesEarned()).contains("first", "legend");
        }

        @Test
        @DisplayName("모든 시·도에 한 번씩 발을 디뎌야 팔도 뱃지를 얻는다")
        void allProvinces() {
            ExplorerProgress progress = 새_진행();

            assertThat(칠한다(progress, 방문(울릉군)).badgesEarned()).doesNotContain("allprov");
            칠한다(progress, 방문(종로구).처리시각(초(2)));
            assertThat(칠한다(progress, 방문(가평군).처리시각(초(3))).badgesEarned()).contains("allprov");
        }

        @Test
        @DisplayName("한 번 얻으면 칠한 곳을 모두 취소해도 사라지지 않는다")
        void neverRevoked() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            칠한다(progress, 방문(중구).처리시각(초(1)));

            취소한다(progress, 방문(중구).처리시각(초(2)));
            취소한다(progress, 방문(종로구).처리시각(초(3)));

            assertThat(progress.badges()).containsKeys("first", "seoul", "half");
            assertThat(progress.titles()).containsKey("own-KR-11");
        }
    }

    @Nested
    @DisplayName("칭호를 고를 때")
    class SelectTitle {

        @Test
        @DisplayName("얻은 칭호를 고르면 그 칭호가 보인다")
        void earnedTitle() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            칠한다(progress, 방문(중구).처리시각(초(1)));

            progress.selectTitle("own-KR-11", 진행규칙, 기준시각);

            assertThat(progress.displayTitle(진행규칙)).isEqualTo("own-KR-11");
        }

        @Test
        @DisplayName("고른 칭호를 비우면 레벨 칭호가 보인다")
        void clearSelection() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            칠한다(progress, 방문(중구).처리시각(초(1)));
            progress.selectTitle("own-KR-11", 진행규칙, 기준시각);

            progress.selectTitle(null, 진행규칙, 기준시각);

            assertThat(progress.displayTitle(진행규칙)).isEqualTo("lv1");
        }

        @Test
        @DisplayName("아직 얻지 못한 칭호는 고를 수 없다")
        void notEarned() {
            assertThatThrownBy(() -> 새_진행().selectTitle("own-KR-11", 진행규칙, 기준시각))
                .isInstanceOf(TerritoryException.class)
                .satisfies(thrown -> assertThat(code(thrown)).isEqualTo("TITLE_NOT_EARNED"));
        }

        @Test
        @DisplayName("없는 칭호는 고를 수 없다")
        void unknown() {
            assertThatThrownBy(() -> 새_진행().selectTitle("nope", 진행규칙, 기준시각))
                .satisfies(thrown -> assertThat(code(thrown)).isEqualTo("TITLE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("연속 탐험 달(스트릭)은")
    class Streaks {

        private static final YearMonth 구월 = YearMonth.of(2026, 9);
        private static final YearMonth 시월 = YearMonth.of(2026, 10);

        @Test
        @DisplayName("체크인 처리 시각의 달로 연속을 센다")
        void countsByProcessingMonth() {
            ExplorerProgress progress = threeMonthsInARow();

            assertThat(progress.streak()).isEqualTo(Streak.of(3, 시월));
        }

        @Test
        @DisplayName("세 달 연속이면 연속 탐험 뱃지를 얻는다")
        void streakBadge() {
            assertThat(threeMonthsInARow().badges()).containsKey("streak3");
        }

        @Test
        @DisplayName("한 달이라도 비면 끊긴다")
        void gapBreaksStreak() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구).처리시각(기준시각.minus(Duration.ofDays(62)))); // 8월

            칠한다(progress, 방문(중구));                                                 // 10월(9월 비어 있음)

            assertThat(progress.streak()).isEqualTo(Streak.of(1, 시월));
        }

        @Test
        @DisplayName("같은 달에 여러 번 칠해도 한 달로 센다")
        void sameMonthCountsOnce() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(중구));
            칠한다(progress, 방문(가평군).처리시각(초(60)));

            assertThat(progress.streak()).isEqualTo(Streak.of(1, 시월));
        }

        @Test
        @DisplayName("다음 달에 칠하면 하나 늘고 같은 달이면 그대로다")
        void nextMonthGrows() {
            Streak streak = Streak.NONE.record(구월);

            assertThat(streak).isEqualTo(Streak.of(1, 구월));
            assertThat(streak.record(구월)).isEqualTo(streak);
            assertThat(streak.record(시월)).isEqualTo(Streak.of(2, 시월));
        }

        @Test
        @DisplayName("끊긴 뒤에는 1부터 다시 센다")
        void restartsFromOne() {
            assertThat(Streak.of(5, YearMonth.of(2026, 7)).record(시월)).isEqualTo(Streak.of(1, 시월));
        }

        @Test
        @DisplayName("이미 센 달보다 이전 달의 체크인은 소급해 세지 않는다")
        void noRetroactiveMonths() {
            Streak streak = Streak.of(2, 시월);

            assertThat(streak.record(구월)).isEqualTo(streak);
        }

        @Test
        @DisplayName("지난달까지 이어졌으면 이번 달에도 그 값이 보인다")
        void shownWhileLastMonthCounted() {
            Streak streak = Streak.of(3, 구월);

            assertThat(streak.asOf(구월)).isEqualTo(3);
            assertThat(streak.asOf(시월)).isEqualTo(3);
        }

        @Test
        @DisplayName("이번 달에 아직 칠하지 않았으면 이번 달 탐험은 이어지지 않은 상태다")
        void notActiveThisMonthYet() {
            assertThat(Streak.of(3, 구월).activeIn(시월)).isFalse();
        }

        @Test
        @DisplayName("두 달 넘게 비었거나 한 번도 칠하지 않았으면 0으로 보인다")
        void shownAsZeroWhenBroken() {
            assertThat(Streak.of(3, 구월).asOf(YearMonth.of(2026, 11))).isZero();
            assertThat(Streak.NONE.asOf(시월)).isZero();
        }

        private ExplorerProgress threeMonthsInARow() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구).처리시각(기준시각.minus(Duration.ofDays(62)))); // 8월
            칠한다(progress, 방문(중구).처리시각(기준시각.minus(Duration.ofDays(31))));   // 9월
            칠한다(progress, 방문(가평군));                                               // 10월
            return progress;
        }
    }

    @Nested
    @DisplayName("XP 장부는")
    class Ledger {

        @Test
        @DisplayName("기본 XP의 지급·회수·재지급을 회차마다 다른 줄로 남긴다")
        void generationsAsSeparateLines() {
            XpLedger ledger = XpLedger.empty();

            assertThat(ledger.grantRegion(나, 종로구, 10, 기준시각)).isTrue();
            assertThat(ledger.revokeRegion(나, 종로구, 기준시각)).isTrue();
            assertThat(ledger.grantRegion(나, 종로구, 10, 기준시각)).isTrue();

            String prefix = "region:" + 나.value() + ":KR-11010#";
            assertThat(ledger.entries()).extracting(XpLedgerEntry::refId)
                .containsExactly(prefix + "1", prefix + "1:revoke", prefix + "2");
        }

        @Test
        @DisplayName("XP 감소는 음수 줄로만 남는다")
        void decreaseAsNegativeLine() {
            XpLedger ledger = XpLedger.empty();
            ledger.grantRegion(나, 종로구, 10, 기준시각);
            ledger.revokeRegion(나, 종로구, 기준시각);
            ledger.grantRegion(나, 종로구, 10, 기준시각);

            assertThat(ledger.entries()).extracting(XpLedgerEntry::amount).containsExactly(10, -10, 10);
            assertThat(ledger.total()).isEqualTo(10);
        }

        @Test
        @DisplayName("되돌리지 않은 기본 XP가 있으면 다시 온 지급은 무시한다")
        void duplicateGrantIgnored() {
            XpLedger ledger = XpLedger.empty();
            ledger.grantRegion(나, 종로구, 10, 기준시각);

            assertThat(ledger.grantRegion(나, 종로구, 10, 기준시각)).isFalse();
            assertThat(ledger.entries()).hasSize(1);
        }

        @Test
        @DisplayName("되돌릴 기본 XP가 없으면 다시 온 회수는 무시한다")
        void duplicateRevokeIgnored() {
            XpLedger ledger = XpLedger.empty();
            ledger.grantRegion(나, 종로구, 10, 기준시각);
            ledger.revokeRegion(나, 종로구, 기준시각);

            assertThat(ledger.revokeRegion(나, 종로구, 기준시각)).isFalse();
            assertThat(ledger.entries()).hasSize(2);
            assertThat(ledger.total()).isZero();
        }

        @Test
        @DisplayName("한 번만 주는 보상은 같은 보상을 두 번 쌓지 않는다")
        void onceOnlyReward() {
            XpLedger ledger = XpLedger.empty();

            assertThat(ledger.grantOnce(XpSource.PROVINCE_FIRST, "province:x:KR-11", 15, 기준시각)).isTrue();
            assertThat(ledger.grantOnce(XpSource.PROVINCE_FIRST, "province:x:KR-11", 15, 기준시각)).isFalse();
            assertThat(ledger.total()).isEqualTo(15);
        }

        @Test
        @DisplayName("저장된 장부에 같은 보상이 두 번 있으면 불러오지 않는다")
        void corruptedLedgerRejected() {
            XpLedgerEntry entry = new XpLedgerEntry(XpSource.QUEST, 60, "quest:x", 기준시각);

            assertThatThrownBy(() -> XpLedger.of(List.of(entry, entry))).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("처음 가는 시·도인지는 내가 밟은 지역 기록으로 정한다")
    class FirstInProvince {

        @Test
        @DisplayName("그 시·도를 밟은 적이 없으면 처음이다")
        void neverVisited() {
            assertThat(새_진행().regions().visitedProvinceBefore("KR-11", 기준시각)).isFalse();
        }

        @Test
        @DisplayName("취소한 지역도 그 시·도를 밟은 기록으로 남는다")
        void cancelledStillCounts() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            취소한다(progress, 방문(종로구).처리시각(초(1)));

            assertThat(progress.regions().visitedProvinceBefore("KR-11", 초(60))).isTrue();
        }

        @Test
        @DisplayName("같은 시각에 칠한 자기 자신은 이전 방문이 아니다")
        void sameInstantIsNotBefore() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            취소한다(progress, 방문(종로구).처리시각(초(1)));

            assertThat(progress.regions().visitedProvinceBefore("KR-11", 기준시각)).isFalse();
        }
    }

    @Nested
    @DisplayName("재계산 출발점을 만들면")
    class RebuildBase {

        @Test
        @DisplayName("지금 멤버인 지도의 활성 지역과 기본 XP를 비운다")
        void clearsCurrentMapsBase() {
            ExplorerProgress base = paintedAndRewarded().rebuildBase(Set.of(지도));

            assertThat(base.regions().find(종로구).orElseThrow().active()).isFalse();
            assertThat(base.ledger().has(RefIds.regionGrant(나, 종로구, 1))).isFalse();
        }

        @Test
        @DisplayName("시·도 첫 발·선점·퀘스트 XP는 남긴다")
        void keepsOnceOnlyRewards() {
            ExplorerProgress base = paintedAndRewarded().rebuildBase(Set.of(지도));

            assertThat(base.xp()).isEqualTo(15 + 10 + 60);
        }

        @Test
        @DisplayName("처음 밟은 시각과 얻은 뱃지는 남긴다")
        void keepsFirstVisitAndBadges() {
            ExplorerProgress base = paintedAndRewarded().rebuildBase(Set.of(지도));

            assertThat(base.regions().find(종로구).orElseThrow().firstVisitedAt()).isEqualTo(초(1));
            assertThat(base.badges()).containsKey("first");
        }

        @Test
        @DisplayName("연속 탐험 달은 비운다")
        void clearsStreak() {
            assertThat(paintedAndRewarded().rebuildBase(Set.of(지도)).streak()).isEqualTo(Streak.NONE);
        }

        @Test
        @DisplayName("떠난 지도로 남은 활성 지역과 그 기본 XP는 그대로 둔다")
        void keepsDepartedMaps() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구).선점아님().회차(1).처리시각(초(1)));                // 지금 멤버인 지도
            칠한다(progress, 방문(가평군).지도(다른지도).선점아님().회차(1).처리시각(초(2))); // 떠난 지도

            ExplorerProgress base = progress.rebuildBase(Set.of(지도));

            assertThat(base.regions().find(종로구).orElseThrow().active()).isFalse();
            assertThat(base.regions().find(가평군).orElseThrow().activeMaps()).containsExactly(다른지도);
            assertThat(base.ledger().has(RefIds.regionGrant(나, 가평군, 1))).isTrue();
            assertThat(base.ledger().has(RefIds.regionGrant(나, 종로구, 1))).isFalse();
        }

        @Test
        @DisplayName("지금 멤버인 지도의 회차 기록은 비우고 다시 칠하면 채운다")
        void clearsMarksOfCurrentMaps() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구).선점아님().회차(3).처리시각(초(1)));
            칠한다(progress, 방문(가평군).지도(다른지도).선점아님().회차(1).처리시각(초(2)));

            ExplorerProgress base = progress.rebuildBase(Set.of(지도));

            assertThat(base.regions().find(종로구).orElseThrow().marks()).isEmpty();
            assertThat(base.regions().find(가평군).orElseThrow().marks()).containsEntry(다른지도, 1);
            칠한다(base, 방문(종로구).선점아님().회차(3).처리시각(초(1)));
            assertThat(base.regions().find(종로구).orElseThrow().marks()).containsEntry(지도, 3);
        }

        /** 종로구를 칠하고(35) 이번 달 퀘스트 보상(60)을 받은 진행. */
        private ExplorerProgress paintedAndRewarded() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구).처리시각(초(1)));
            progress.applyQuestReward(QuestPeriod.of(YearMonth.of(2026, 10)), "m3", 60, 초(2), 진행규칙);
            return progress;
        }
    }
}
