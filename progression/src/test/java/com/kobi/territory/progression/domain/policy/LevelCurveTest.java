package com.kobi.territory.progression.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LevelCurveTest {

    private final LevelCurve curve = LevelCurve.withDivisor(5);

    @Test
    void 레벨_경계값은_20L_L마이너스1() {
        assertThat(curve.levelOf(0)).isEqualTo(1);
        assertThat(curve.levelOf(39)).isEqualTo(1);
        assertThat(curve.levelOf(40)).isEqualTo(2);
        assertThat(curve.levelOf(119)).isEqualTo(2);
        assertThat(curve.levelOf(120)).isEqualTo(3);
        assertThat(curve.levelOf(239)).isEqualTo(3);
        assertThat(curve.levelOf(240)).isEqualTo(4);
        assertThat(curve.threshold(1)).isZero();
        assertThat(curve.threshold(16)).isEqualTo(4800);
    }

    @Test
    void 프로토타입_부동소수_공식과_0부터_20000까지_같다() {
        for (int xp = 0; xp <= 20_000; xp++) {
            int proto = (int) Math.floor((1 + Math.sqrt(1 + xp / 5.0)) / 2);
            assertThat(curve.levelOf(xp)).as("xp=%d", xp).isEqualTo(proto);
        }
    }
}
