package com.kobi.territory.exploration.domain.policy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("체크인 규칙")
class CheckInPolicyTest {

    @Test
    @DisplayName("하루 상한은 한 곳 이상이어야 한다")
    void capAtLeastOne() {
        assertThatThrownBy(() -> new CheckInPolicy(0, Duration.ZERO, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("온보딩 기간은 음수일 수 없다")
    void graceNotNegative() {
        assertThatThrownBy(() -> new CheckInPolicy(5, Duration.ofHours(-1), false)).isInstanceOf(IllegalArgumentException.class);
    }
}
