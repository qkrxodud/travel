package com.kobi.territory.exploration.domain.explorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class AccessTokenTest {

    @Test
    void 토큰은_길고_매번_다르며_해시만_저장한다() {
        SecureRandom random = new SecureRandom();
        AccessToken first = AccessToken.generate(random);
        AccessToken second = AccessToken.generate(random);
        assertThat(first.value()).hasSize(43).isNotEqualTo(second.value());
        assertThat(first.hash().value()).hasSize(64).isEqualTo(AccessTokenHash.of(new AccessToken(first.value())).value());
        assertThat(first.hash()).isNotEqualTo(second.hash());
        assertThat(first.toString()).doesNotContain(first.value());
    }

    @Test
    void 헤더_값_파싱() {
        assertThat(AccessToken.parse(null)).isEmpty();
        assertThat(AccessToken.parse("  ")).isEmpty();
        assertThat(AccessToken.parse("x".repeat(AccessToken.MAX_LENGTH + 1))).isEmpty();
        assertThat(AccessToken.parse(" abc ")).map(AccessToken::value).contains("abc");
    }
}
