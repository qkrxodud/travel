package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.progression.domain.quest.QuestPeriod;
import static com.kobi.territory.progression.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.progression.domain.Fixtures.JONGNO;
import static com.kobi.territory.progression.domain.Fixtures.JUNG;
import static com.kobi.territory.progression.domain.Fixtures.MAP;
import static com.kobi.territory.progression.domain.Fixtures.MAP2;
import static com.kobi.territory.progression.domain.Fixtures.ME;
import static com.kobi.territory.progression.domain.Fixtures.POLICY;
import static com.kobi.territory.progression.domain.Fixtures.T0;
import static com.kobi.territory.progression.domain.Fixtures.ULLEUNG;
import static com.kobi.territory.progression.domain.Fixtures.visit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExplorerProgressTest {

    static ExplorerProgress fresh() {
        return ExplorerProgress.start(ME, POLICY, T0);
    }

    static Instant at(int sec) {
        return T0.plusSeconds(sec);
    }

    @Nested
    @DisplayName("XP·레벨")
    class Xp {
        @Test
        void 시작은_XP_0_레벨_1_레벨1_칭호() {
            ExplorerProgress progress = fresh();
            assertThat(progress.xp()).isZero();
            assertThat(progress.level()).isEqualTo(1);
            assertThat(progress.titles()).containsKey("lv1");
            assertThat(progress.displayTitle(POLICY)).isEqualTo("lv1");
        }

        @Test
        void 체크인_보상은_기본_시도첫발_선점이고_XP는_장부_합계() {
            ExplorerProgress progress = fresh();
            ProgressChange c1 = progress.applyVisit(visit(JONGNO, at(1)), POLICY);
            assertThat(c1.xpDelta()).isEqualTo(10 + 15 + 10);
            assertThat(c1.levelUp()).isEmpty();
            ProgressChange c2 = progress.applyVisit(visit(JUNG, at(2)), POLICY);
            assertThat(c2.xpDelta()).isEqualTo(10 + 10); // 서울은 이미 밟음
            assertThat(c2.levelUp()).contains(2);         // 55 ≥ 40
            assertThat(progress.xp()).isEqualTo(55).isEqualTo(progress.ledger().total());
            assertThat(progress.ledger().entries().stream().mapToInt(XpLedgerEntry::amount).sum()).isEqualTo(55);
        }

        @Test
        void 같은_RegionVisited가_두번_와도_한번만_반영된다() {
            ExplorerProgress progress = fresh();
            progress.applyVisit(visit(GAPYEONG, at(1)), POLICY);
            ProgressChange again = progress.applyVisit(visit(GAPYEONG, at(1)), POLICY);
            assertThat(again.xpDelta()).isZero();
            assertThat(progress.xp()).isEqualTo(20 + 15 + 10);
            assertThat(progress.regions().find(GAPYEONG).orElseThrow().activeMapCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("여러 지도·취소(D2·D3·D4)")
    class Cancel {
        @Test
        void 같은_지역을_두_지도에서_칠하면_기본_XP는_한번_선점은_지도마다() {
            ExplorerProgress progress = fresh();
            progress.applyVisit(visit(MAP, JONGNO, at(1), true), POLICY);
            ProgressChange second = progress.applyVisit(visit(MAP2, JONGNO, at(2), true), POLICY);
            assertThat(second.xpDelta()).isEqualTo(10); // 선점만(기본·시·도 없음)
            assertThat(progress.regions().find(JONGNO).orElseThrow().activeMapCount()).isEqualTo(2);
        }

        @Test
        void 다른_지도에_남아_있으면_기본_XP를_회수하지_않고_모두_사라지면_회수한다() {
            ExplorerProgress progress = fresh();
            progress.applyVisit(visit(MAP, JONGNO, at(1), true), POLICY);
            progress.applyVisit(visit(MAP2, JONGNO, at(2), true), POLICY);
            assertThat(progress.revokeVisit(MAP, JONGNO, at(3), POLICY).xpDelta()).isZero();
            assertThat(progress.revokeVisit(MAP2, JONGNO, at(4), POLICY).xpDelta()).isEqualTo(-10);
            assertThat(progress.regions().find(JONGNO).orElseThrow().active()).isFalse();
        }

        @Test
        void 취소는_기본_XP만_되돌리고_시도_첫발_선점은_유지한다_재전달은_no_op() {
            ExplorerProgress progress = fresh();
            progress.applyVisit(visit(JONGNO, at(1)), POLICY);
            ProgressChange change = progress.revokeVisit(MAP, JONGNO, at(2), POLICY);
            assertThat(change.xpDelta()).isEqualTo(-10);
            assertThat(progress.xp()).isEqualTo(25);
            assertThat(progress.revokeVisit(MAP, JONGNO, at(3), POLICY).xpDelta()).isZero();
        }

        @Test
        void 취소_후_재체크인은_기본_XP를_다음_세대로_다시_준다_시도_보너스는_다시_안_준다() {
            ExplorerProgress progress = fresh();
            progress.applyVisit(visit(JONGNO, at(1)), POLICY);
            progress.revokeVisit(MAP, JONGNO, at(2), POLICY);
            ProgressChange again = progress.applyVisit(visit(JONGNO, at(3)), POLICY);
            assertThat(again.xpDelta()).isEqualTo(10); // 기본만 — 시·도 첫 발·선점은 이미 받음
            assertThat(progress.ledger().has("region:" + ME.value() + ":KR-11010#2")).isTrue();
            assertThat(progress.xp()).isEqualTo(35);
        }

        @Test
        void 시도_첫발은_탐험가_기준이라_다른_지도의_같은_시도는_다시_주지_않는다() {
            ExplorerProgress progress = fresh();
            progress.applyVisit(visit(MAP, JONGNO, at(1), true), POLICY);
            ProgressChange change = progress.applyVisit(visit(MAP2, JUNG, at(2), true), POLICY);
            assertThat(change.xpDelta()).isEqualTo(10 + 10);
        }
    }

    @Nested
    @DisplayName("스트릭")
    class Streaks {
        @Test
        void 처리_시각의_달로_연속을_센다() {
            ExplorerProgress progress = fresh();
            progress.applyVisit(visit(JONGNO, T0.minus(Duration.ofDays(62))), POLICY); // 8월
            progress.applyVisit(visit(JUNG, T0.minus(Duration.ofDays(31))), POLICY);   // 9월
            progress.applyVisit(visit(GAPYEONG, T0), POLICY);                          // 10월
            assertThat(progress.streak()).isEqualTo(Streak.of(3, YearMonth.of(2026, 10)));
            assertThat(progress.badges()).containsKey("streak3");
        }

        @Test
        void 같은_달_여러_번은_한_번이고_빈_달이_있으면_끊긴다() {
            ExplorerProgress progress = fresh();
            progress.applyVisit(visit(JONGNO, T0.minus(Duration.ofDays(62))), POLICY); // 8월
            progress.applyVisit(visit(JUNG, T0), POLICY);                              // 10월(9월 비어 있음)
            progress.applyVisit(visit(GAPYEONG, T0.plusSeconds(60)), POLICY);          // 10월
            assertThat(progress.streak()).isEqualTo(Streak.of(1, YearMonth.of(2026, 10)));
        }
    }

    @Nested
    @DisplayName("뱃지·칭호")
    class BadgesAndTitles {
        @Test
        void 뱃지와_칭호는_추가만_취소해도_회수하지_않는다() {
            ExplorerProgress progress = fresh();
            progress.applyVisit(visit(JONGNO, at(1)), POLICY);
            ProgressChange change = progress.applyVisit(visit(JUNG, at(2)), POLICY);
            assertThat(change.badgesEarned()).contains("seoul");
            assertThat(change.titlesEarned()).contains("own-KR-11");
            assertThat(progress.badges()).containsKeys("first", "seoul", "half"); // 3곳 중 2곳 = 67%
            progress.revokeVisit(MAP, JUNG, at(3), POLICY);
            progress.revokeVisit(MAP, JONGNO, at(4), POLICY);
            assertThat(progress.badges()).containsKeys("first", "seoul", "half");
            assertThat(progress.titles()).containsKey("own-KR-11");
        }

        @Test
        void 전설_팔도_뱃지() {
            ExplorerProgress progress = fresh();
            ProgressChange change = progress.applyVisit(visit(ULLEUNG, at(1)), POLICY);
            assertThat(change.badgesEarned()).contains("first", "legend").doesNotContain("allprov");
            progress.applyVisit(visit(JONGNO, at(2)), POLICY);
            assertThat(progress.applyVisit(visit(GAPYEONG, at(3)), POLICY).badgesEarned()).contains("allprov");
        }

        @Test
        void 세트_완성_보너스는_탐험가당_세트당_한번_칭호와_뱃지() {
            ExplorerProgress progress = fresh();
            ProgressChange change = progress.applyThemeCompleted("han", at(1), POLICY);
            assertThat(change.xpDelta()).isEqualTo(100);
            assertThat(change.titlesEarned()).contains("set-han").doesNotContain("lv3"); // 100 < 120
            assertThat(change.badgesEarned()).contains("set1");
            assertThat(change.levelUp()).contains(2);
            assertThat(progress.applyThemeCompleted("han", at(2), POLICY).xpDelta()).isZero();
        }

        @Test
        void 칭호_선택은_얻은_것만() {
            ExplorerProgress progress = fresh();
            assertThatThrownBy(() -> progress.selectTitle("own-KR-11", POLICY, T0)).isInstanceOf(TerritoryException.class)
                .extracting(thrown -> ((TerritoryException) thrown).code()).isEqualTo("TITLE_NOT_EARNED");
            assertThatThrownBy(() -> progress.selectTitle("nope", POLICY, T0))
                .extracting(thrown -> ((TerritoryException) thrown).code()).isEqualTo("TITLE_NOT_FOUND");
            progress.applyVisit(visit(JONGNO, at(1)), POLICY);
            progress.applyVisit(visit(JUNG, at(2)), POLICY);
            progress.selectTitle("own-KR-11", POLICY, T0);
            assertThat(progress.displayTitle(POLICY)).isEqualTo("own-KR-11");
            progress.selectTitle(null, POLICY, T0);
            assertThat(progress.displayTitle(POLICY)).isEqualTo("lv1");
        }

        @Test
        void 상시_도전_보상을_받으면_그_칭호() {
            ExplorerProgress progress = fresh();
            ProgressChange change = progress.applyQuestReward(QuestPeriod.ALL, "leg5", 150, at(1), POLICY);
            assertThat(change.xpDelta()).isEqualTo(150);
            assertThat(change.titlesEarned()).contains("long-leg5", "lv3");
            assertThat(progress.applyQuestReward(QuestPeriod.ALL, "leg5", 150, at(2), POLICY).xpDelta()).isZero();
        }
    }

    @Test
    void rebuildBase는_기본_XP_지역_활성_스트릭만_비운다() {
        ExplorerProgress progress = fresh();
        progress.applyVisit(visit(JONGNO, at(1)), POLICY);
        progress.applyQuestReward(QuestPeriod.of(YearMonth.of(2026, 10)), "m3", 60, at(2), POLICY);
        ExplorerProgress base = progress.rebuildBase(java.util.Set.of(MAP));
        assertThat(base.xp()).isEqualTo(15 + 10 + 60); // 시·도 첫 발·선점·퀘스트 유지, 기본 10만 빠짐
        assertThat(base.regions().find(JONGNO).orElseThrow().active()).isFalse();
        assertThat(base.regions().find(JONGNO).orElseThrow().firstVisitedAt()).isEqualTo(at(1));
        assertThat(base.badges()).containsKey("first");
        assertThat(base.streak()).isEqualTo(Streak.NONE);
    }

    @Test
    void 처음_가는_시도_판정은_탐험가의_지역_기록_기준이다_QA_P3_3() {
        ExplorerProgress progress = fresh();
        assertThat(progress.regions().visitedProvinceBefore("KR-11", T0)).isFalse();
        progress.applyVisit(visit(JONGNO, T0), POLICY);
        progress.revokeVisit(MAP, JONGNO, T0.plusSeconds(1), POLICY);
        // 취소해 비활성이어도 같은 시·도의 이후 체크인은 처음이 아니다
        assertThat(progress.regions().visitedProvinceBefore("KR-11", T0.plusSeconds(60))).isTrue();
        // 처리 순서와 무관하게 같은 답: 자기 자신(같은 시각)은 "이전"이 아니다
        assertThat(progress.regions().visitedProvinceBefore("KR-11", T0)).isFalse();
    }
}
