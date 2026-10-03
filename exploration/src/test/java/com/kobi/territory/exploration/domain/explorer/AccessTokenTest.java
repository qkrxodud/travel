package com.kobi.territory.exploration.domain.explorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("익명 접근 토큰")
class AccessTokenTest {

    @Nested
    @DisplayName("발급할 때")
    class Issue {

        @Test
        @DisplayName("길고 매번 다르다")
        void longAndUnique() {
            SecureRandom random = new SecureRandom();
            AccessToken first = AccessToken.generate(random);
            AccessToken second = AccessToken.generate(random);
            assertThat(first.value()).hasSize(43).isNotEqualTo(second.value());
            assertThat(first.hash()).isNotEqualTo(second.hash());
        }

        @Test
        @DisplayName("서버에는 토큰 대신 다시 계산할 수 있는 지문만 남긴다")
        void storesFingerprintOnly() {
            AccessToken token = AccessToken.generate(new SecureRandom());
            assertThat(token.hash().value()).hasSize(64).isEqualTo(AccessTokenHash.of(new AccessToken(token.value())).value());
        }

        @Test
        @DisplayName("기록에 찍혀도 토큰 값이 드러나지 않는다")
        void notLogged() {
            AccessToken token = AccessToken.generate(new SecureRandom());
            assertThat(token.toString()).doesNotContain(token.value());
        }
    }

    @Nested
    @DisplayName("요청에 실려 올 때")
    class Parse {

        @Test
        @DisplayName("비었거나 지나치게 길면 토큰이 없는 것이다")
        void blankOrTooLong() {
            assertThat(AccessToken.parse(null)).isEmpty();
            assertThat(AccessToken.parse("  ")).isEmpty();
            assertThat(AccessToken.parse("x".repeat(AccessToken.MAX_LENGTH + 1))).isEmpty();
        }

        @Test
        @DisplayName("앞뒤 공백은 잘라 읽는다")
        void trimmed() {
            assertThat(AccessToken.parse(" abc ")).map(AccessToken::value).contains("abc");
        }
    }
}
