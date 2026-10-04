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
    private static final String VAPID = "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM";
    private static final String SUBJECT = "mailto:ops@territory.kr";

    @Test
    @DisplayName("강한 랜덤 값이면 운영을 시작한다")
    void strong() {
        new ProductionSecrets(STRONG, STRONG + "a", STRONG + "b", VAPID, SUBJECT);
        assertThat(ProductionSecrets.weakness(STRONG)).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" 이면 시작하지 않는다")
    @ValueSource(strings = {"change-me", "CHANGE-ME-please-now-1234", "local-analytics-salt", "short-random-1", "   ",
        "my-example-value-1234567"})
    @DisplayName("비었거나 16자보다 짧거나 예시·로컬 기본값이면 운영을 시작하지 않는다 — 값은 알리지 않는다")
    void weak(String value) {
        assertThatThrownBy(() -> new ProductionSecrets(STRONG, STRONG, value, VAPID, SUBJECT)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TERRITORY_ANALYTICS_SALT")
            .satisfies(rejected -> assertThat(value.isBlank() || !rejected.getMessage().contains(value)).isTrue());
    }

    @Test
    @DisplayName("관리자 토큰과 미스터리 비밀값도 같은 기준이다")
    void sameRuleForAll() {
        assertThatThrownBy(() -> new ProductionSecrets("change-me", STRONG, STRONG + "a", VAPID, SUBJECT)).hasMessageContaining("TERRITORY_ADMIN_TOKEN");
        assertThatThrownBy(() -> new ProductionSecrets(STRONG, "local-mystery-salt", STRONG, VAPID, SUBJECT))
            .hasMessageContaining("TERRITORY_MYSTERY_SALT");
    }

    @Test
    @DisplayName("분석 비밀값이 미스터리 비밀값과 같으면 운영을 시작하지 않는다")
    void analyticsSaltDiffersFromMystery() {
        assertThatThrownBy(() -> new ProductionSecrets(STRONG, STRONG + "x", STRONG + "x", VAPID, SUBJECT))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("TERRITORY_ANALYTICS_SALT")
            .hasMessageContaining("TERRITORY_MYSTERY_SALT").satisfies(rejected -> assertThat(rejected.getMessage()).doesNotContain(STRONG));
    }

    @Test
    @DisplayName("알림 키가 비었거나 로컬 시험용 키면 운영을 시작하지 않는다")
    void vapidKeyIsNotLocal() {
        assertThatThrownBy(() -> new ProductionSecrets(STRONG, STRONG + "a", STRONG + "b", ProductionSecrets.LOCAL_VAPID_PUBLIC_KEY, SUBJECT))
            .hasMessageContaining("TERRITORY_VAPID_PUBLIC_KEY");
        assertThatThrownBy(() -> new ProductionSecrets(STRONG, STRONG + "a", STRONG + "b", " ", SUBJECT))
            .hasMessageContaining("TERRITORY_VAPID_PUBLIC_KEY");
    }

    @Test
    @DisplayName("알림 연락처가 로컬·예시 주소면 운영을 시작하지 않는다")
    void vapidSubjectIsNotLocal() {
        assertThatThrownBy(() -> new ProductionSecrets(STRONG, STRONG + "a", STRONG + "b", VAPID, "mailto:dev@territory.local"))
            .hasMessageContaining("TERRITORY_VAPID_SUBJECT");
        assertThatThrownBy(() -> new ProductionSecrets(STRONG, STRONG + "a", STRONG + "b", VAPID, "mailto:change-me@example.com"))
            .hasMessageContaining("TERRITORY_VAPID_SUBJECT");
    }
}
