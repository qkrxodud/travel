package com.kobi.territory.progression.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class StreakTest {

    static final YearMonth SEP = YearMonth.of(2026, 9);
    static final YearMonth OCT = YearMonth.of(2026, 10);

    @Test
    void 연속된_달은_늘고_같은_달은_그대로() {
        Streak streak = Streak.NONE.record(SEP);
        assertThat(streak).isEqualTo(new Streak(1, SEP));
        assertThat(streak.record(SEP)).isEqualTo(streak);
        assertThat(streak.record(OCT)).isEqualTo(new Streak(2, OCT));
    }

    @Test
    void 한_달이라도_비면_끊겨서_1부터() {
        Streak streak = new Streak(5, YearMonth.of(2026, 7)).record(OCT);
        assertThat(streak).isEqualTo(new Streak(1, OCT));
    }

    @Test
    void 과거_달_이벤트는_무시한다() {
        Streak streak = new Streak(2, OCT);
        assertThat(streak.record(SEP)).isEqualTo(streak);
    }

    @Test
    void 표시값은_지난달까지_이어졌으면_유지_그보다_오래면_0() {
        Streak streak = new Streak(3, SEP);
        assertThat(streak.asOf(SEP)).isEqualTo(3);
        assertThat(streak.asOf(OCT)).isEqualTo(3);
        assertThat(streak.activeIn(OCT)).isFalse();
        assertThat(streak.asOf(YearMonth.of(2026, 11))).isZero();
        assertThat(Streak.NONE.asOf(OCT)).isZero();
    }
}
