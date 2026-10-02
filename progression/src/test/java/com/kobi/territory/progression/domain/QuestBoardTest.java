package com.kobi.territory.progression.domain;

import static com.kobi.territory.progression.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.progression.domain.Fixtures.JONGNO;
import static com.kobi.territory.progression.domain.Fixtures.JUNG;
import static com.kobi.territory.progression.domain.Fixtures.ME;
import static com.kobi.territory.progression.domain.Fixtures.QUESTS;
import static com.kobi.territory.progression.domain.Fixtures.T0;
import static com.kobi.territory.progression.domain.Fixtures.ULLEUNG;
import static com.kobi.territory.progression.domain.Fixtures.fact;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.model.RegionCode;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** QuestBoard: 같은 지역 한 번만 셈, 보상 1회, 지난 달 보드 불변. */
class QuestBoardTest {

    static final YearMonth OCT = YearMonth.of(2026, 10);
    static final QuestPeriod OCT_P = QuestPeriod.of(OCT);

    static String code(Throwable thrown) {
        return ((TerritoryException) thrown).code();
    }

    @Test
    void 지표별로_세고_같은_지역은_한_번만() {
        QuestBoard board = QuestBoard.empty(ME, OCT_P);
        board.applyVisit(fact(JONGNO, true), QUESTS, OCT);
        board.applyVisit(fact(JONGNO, true), QUESTS, OCT); // 재전달
        board.applyVisit(fact(GAPYEONG, true), QUESTS, OCT);
        var m3 = QUESTS.require("m3");
        assertThat(board.of("m3").current(m3)).isEqualTo(2);
        assertThat(board.of("mgun").achieved(QUESTS.require("mgun"))).isTrue();   // 가평(희귀)
        assertThat(board.of("mprov").current(QUESTS.require("mprov"))).isEqualTo(1);
        assertThat(board.of("mset").current(QUESTS.require("mset"))).isEqualTo(2); // 종로(han)·가평(mix)
        assertThat(board.of("leg5").current(QUESTS.require("leg5"))).isZero();     // 월간 보드엔 상시 퀘스트 없음
        board.applyVisit(fact(JUNG, false), QUESTS, OCT);
        board.applyVisit(fact(ULLEUNG, true), QUESTS, OCT);
        assertThat(board.of("m3").current(m3)).isEqualTo(3); // 목표에서 자른다
        assertThat(board.of("m3").tally().keys()).hasSize(3);
    }

    @Test
    void 시도별_지표는_시도당_param개까지만_센다() {
        QuestBoard all = QuestBoard.empty(ME, QuestPeriod.ALL);
        all.applyVisit(fact(JONGNO, true), QUESTS, OCT);
        all.applyVisit(fact(JUNG, false), QUESTS, OCT);
        all.applyVisit(fact(RegionCode.of("KR-11030"), false), QUESTS, OCT);
        var p3 = QUESTS.require("p3");
        assertThat(all.of("p3").current(p3)).isEqualTo(1);
        assertThat(all.of("p3").tally().keys()).hasSize(2);
        all.applyVisit(fact(GAPYEONG, true), QUESTS, OCT);
        all.applyVisit(fact(RegionCode.of("KR-31380"), false), QUESTS, OCT);
        assertThat(all.of("p3").achieved(p3)).isTrue();
    }

    @Test
    void 보상은_달성_후_한_번만() {
        QuestBoard board = QuestBoard.empty(ME, OCT_P);
        assertThatThrownBy(() -> board.claim("mgun", QUESTS, T0, OCT)).satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_NOT_COMPLETED"));
        board.applyVisit(fact(GAPYEONG, true), QUESTS, OCT);
        QuestReward reward = board.claim("mgun", QUESTS, T0, OCT);
        assertThat(reward.xp()).isEqualTo(40);
        assertThat(reward.period()).isEqualTo(OCT_P);
        assertThat(board.of("mgun").claimed()).isTrue();
        assertThat(board.of("mgun").claimable(QUESTS.require("mgun"))).isFalse();
        assertThatThrownBy(() -> board.claim("mgun", QUESTS, T0, OCT)).satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_ALREADY_CLAIMED"));
        assertThatThrownBy(() -> board.claim("nope", QUESTS, T0, OCT)).satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_NOT_FOUND"));
        assertThatThrownBy(() -> board.claim("leg5", QUESTS, T0, OCT)).satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_NOT_FOUND"));
    }

    @Test
    void 지난_달_보드는_진행도_보상_받기도_바뀌지_않는다() {
        QuestPeriod sep = QuestPeriod.of(YearMonth.of(2026, 9));
        QuestBoard board = QuestBoard.empty(ME, sep);
        board.applyVisit(fact(GAPYEONG, true), QUESTS, YearMonth.of(2026, 9));
        board.applyVisit(fact(JONGNO, true), QUESTS, OCT); // 10월에 처리된 9월 보드 반영 → 무시
        assertThat(board.of("m3").tally().keys()).hasSize(1);
        assertThatThrownBy(() -> board.claim("mgun", QUESTS, T0, OCT)).satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_BOARD_CLOSED"));
        assertThat(QuestPeriod.ALL.closedAt(OCT.plusYears(5))).isFalse();
    }

    @Test
    void 처음_가는_시도는_탐험가의_지역_기록_기준이다_QA_P3_3() {
        ExploredRegions explored = ExploredRegions.empty();
        ProgressVisit seoulBefore = Fixtures.visit(JONGNO, T0);
        assertThat(QuestFact.of(seoulBefore, Fixtures.SETS, explored).firstInProvince()).isTrue();
        // 진행이 기록한 뒤(취소해 비활성이어도) 같은 시·도의 다른 지역은 처음이 아니다
        ExplorerProgress progress = ExplorerProgress.start(ME, Fixtures.POLICY, T0);
        progress.applyVisit(seoulBefore, Fixtures.POLICY);
        progress.revokeVisit(Fixtures.MAP, JONGNO, T0.plusSeconds(1), Fixtures.POLICY);
        assertThat(QuestFact.of(Fixtures.visit(JUNG, T0.plusSeconds(60)), Fixtures.SETS, progress.regions()).firstInProvince())
            .isFalse();
        // 같은 체크인을 처리 순서와 무관하게 판단: 자기 자신(같은 시각)은 "이전"이 아니다
        assertThat(QuestFact.of(seoulBefore, Fixtures.SETS, progress.regions()).firstInProvince()).isTrue();
    }
}
