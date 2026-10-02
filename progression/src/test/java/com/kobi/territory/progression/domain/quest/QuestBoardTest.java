package com.kobi.territory.progression.domain.quest;

import static com.kobi.territory.progression.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.progression.domain.Fixtures.JONGNO;
import static com.kobi.territory.progression.domain.Fixtures.JUNG;
import static com.kobi.territory.progression.domain.Fixtures.ME;
import static com.kobi.territory.progression.domain.Fixtures.QUEST_RULES;
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
        board.applyVisit(fact(JONGNO, true), QUEST_RULES, OCT);
        board.applyVisit(fact(JONGNO, true), QUEST_RULES, OCT); // 재전달
        board.applyVisit(fact(GAPYEONG, true), QUEST_RULES, OCT);
        var m3 = QUEST_RULES.require("m3");
        assertThat(board.of("m3").current(m3)).isEqualTo(2);
        assertThat(board.of("mgun").achieved(QUEST_RULES.require("mgun"))).isTrue();   // 가평(희귀)
        assertThat(board.of("mprov").current(QUEST_RULES.require("mprov"))).isEqualTo(1);
        assertThat(board.of("mset").current(QUEST_RULES.require("mset"))).isEqualTo(2); // 종로(han)·가평(mix)
        assertThat(board.of("leg5").current(QUEST_RULES.require("leg5"))).isZero();     // 월간 보드엔 상시 퀘스트 없음
        board.applyVisit(fact(JUNG, false), QUEST_RULES, OCT);
        board.applyVisit(fact(ULLEUNG, true), QUEST_RULES, OCT);
        assertThat(board.of("m3").current(m3)).isEqualTo(3); // 목표에서 자른다
        assertThat(board.of("m3").tally().size()).isEqualTo(3);
    }

    @Test
    void 시도별_지표는_시도당_param개까지만_센다() {
        QuestBoard all = QuestBoard.empty(ME, QuestPeriod.ALL);
        all.applyVisit(fact(JONGNO, true), QUEST_RULES, OCT);
        all.applyVisit(fact(JUNG, false), QUEST_RULES, OCT);
        all.applyVisit(fact(RegionCode.of("KR-11030"), false), QUEST_RULES, OCT);
        var p3 = QUEST_RULES.require("p3");
        assertThat(all.of("p3").current(p3)).isEqualTo(1);
        assertThat(all.of("p3").tally().size()).isEqualTo(2);
        all.applyVisit(fact(GAPYEONG, true), QUEST_RULES, OCT);
        all.applyVisit(fact(RegionCode.of("KR-31380"), false), QUEST_RULES, OCT);
        assertThat(all.of("p3").achieved(p3)).isTrue();
    }

    @Test
    void 보상은_달성_후_한_번만() {
        QuestBoard board = QuestBoard.empty(ME, OCT_P);
        assertThatThrownBy(() -> board.claim("mgun", QUEST_RULES, T0, OCT)).satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_NOT_COMPLETED"));
        board.applyVisit(fact(GAPYEONG, true), QUEST_RULES, OCT);
        QuestReward reward = board.claim("mgun", QUEST_RULES, T0, OCT);
        assertThat(reward.xp()).isEqualTo(40);
        assertThat(reward.period()).isEqualTo(OCT_P);
        assertThat(board.of("mgun").claimed()).isTrue();
        assertThat(board.of("mgun").claimable(QUEST_RULES.require("mgun"))).isFalse();
        assertThatThrownBy(() -> board.claim("mgun", QUEST_RULES, T0, OCT)).satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_ALREADY_CLAIMED"));
        assertThatThrownBy(() -> board.claim("nope", QUEST_RULES, T0, OCT)).satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_NOT_FOUND"));
        assertThatThrownBy(() -> board.claim("leg5", QUEST_RULES, T0, OCT)).satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_NOT_FOUND"));
    }

    @Test
    void 지난_달_보드는_진행도_보상_받기도_바뀌지_않는다() {
        QuestPeriod sep = QuestPeriod.of(YearMonth.of(2026, 9));
        QuestBoard board = QuestBoard.empty(ME, sep);
        board.applyVisit(fact(GAPYEONG, true), QUEST_RULES, YearMonth.of(2026, 9));
        board.applyVisit(fact(JONGNO, true), QUEST_RULES, OCT); // 10월에 처리된 9월 보드 반영 → 무시
        assertThat(board.of("m3").tally().size()).isEqualTo(1);
        assertThatThrownBy(() -> board.claim("mgun", QUEST_RULES, T0, OCT)).satisfies(thrown -> assertThat(code(thrown)).isEqualTo("QUEST_BOARD_CLOSED"));
        assertThat(QuestPeriod.ALL.closedAt(OCT.plusYears(5))).isFalse();
    }

    @Test
    void 처음_가는_시도는_이전_방문_여부의_반대다() {
        assertThat(QuestFact.of(JONGNO, "KR-11", com.kobi.territory.common.model.Rarity.COMMON, true, false).firstInProvince())
            .isTrue();
        assertThat(QuestFact.of(JONGNO, "KR-11", com.kobi.territory.common.model.Rarity.COMMON, true, true).firstInProvince())
            .isFalse();
    }
}
