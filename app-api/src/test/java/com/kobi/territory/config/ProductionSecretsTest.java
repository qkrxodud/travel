package com.kobi.territory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("운영 비밀값")
class ProductionSecretsTest {

    private static final String STRONG = "q8Zr2LwXk3VmT7pYc4NsB9dHf6Jg";

    @Test
    @DisplayName("강한 랜덤 값이면 운영을 시작한다")
    void strong() {
        new ProductionSecrets(STRONG, STRONG + "a", STRONG + "b");
        assertThat(ProductionSecrets.weakness(STRONG)).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" 이면 시작하지 않는다")
    @ValueSource(strings = {"change-me", "CHANGE-ME-please-now-1234", "local-analytics-salt", "short-random-1", "   ",
        "my-example-value-1234567"})
    @DisplayName("비었거나 16자보다 짧거나 예시·로컬 기본값이면 운영을 시작하지 않는다 — 값은 알리지 않는다")
    void weak(String value) {
        assertThatThrownBy(() -> new ProductionSecrets(STRONG, STRONG, value)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TERRITORY_ANALYTICS_SALT")
            .satisfies(rejected -> assertThat(value.isBlank() || !rejected.getMessage().contains(value)).isTrue());
    }

    @Test
    @DisplayName("관리자 토큰과 미스터리 비밀값도 같은 기준이다")
    void sameRuleForAll() {
        assertThatThrownBy(() -> new ProductionSecrets("change-me", STRONG, STRONG)).hasMessageContaining("TERRITORY_ADMIN_TOKEN");
        assertThatThrownBy(() -> new ProductionSecrets(STRONG, "local-mystery-salt", STRONG))
            .hasMessageContaining("TERRITORY_MYSTERY_SALT");
    }
}
