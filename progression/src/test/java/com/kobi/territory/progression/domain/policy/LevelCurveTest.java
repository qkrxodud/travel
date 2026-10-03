package com.kobi.territory.progression.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 레벨 곡선 floor((1+√(1+xp/5))/2) — 정수형: 레벨 L 하한 = 4·d·L·(L−1), d = 5. */
@DisplayName("레벨 곡선")
class LevelCurveTest {

    private final LevelCurve curve = LevelCurve.withDivisor(5);

    @Test
    @DisplayName("레벨 L의 하한 XP는 20·L·(L−1)이다")
    void thresholds() {
        assertThat(curve.threshold(1)).isZero();
        assertThat(curve.threshold(2)).isEqualTo(40);
        assertThat(curve.threshold(16)).isEqualTo(4800);
    }

    @Test
    @DisplayName("하한에 닿는 순간 다음 레벨이 되고 한 점 모자라면 그대로다")
    void boundaries() {
        assertThat(curve.levelOf(0)).isEqualTo(1);
        assertThat(curve.levelOf(39)).isEqualTo(1);
        assertThat(curve.levelOf(40)).isEqualTo(2);
        assertThat(curve.levelOf(119)).isEqualTo(2);
        assertThat(curve.levelOf(120)).isEqualTo(3);
        assertThat(curve.levelOf(239)).isEqualTo(3);
        assertThat(curve.levelOf(240)).isEqualTo(4);
    }

    @Test
    @DisplayName("0부터 20000 XP까지 기획한 레벨 공식대로 레벨을 준다")
    void matchesPrototypeFormula() {
        for (int xp = 0; xp <= 20_000; xp++) {
            int proto = (int) Math.floor((1 + Math.sqrt(1 + xp / 5.0)) / 2);
            assertThat(curve.levelOf(xp)).as("xp=%d", xp).isEqualTo(proto);
        }
    }
}
