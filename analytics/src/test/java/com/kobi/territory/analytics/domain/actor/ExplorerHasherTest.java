package com.kobi.territory.analytics.domain.actor;

import static com.kobi.territory.analytics.domain.Fixtures.EXPLORER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("탐험가 해시")
class ExplorerHasherTest {

    @Test
    @DisplayName("같은 비밀값이면 다시 시작해도 같은 탐험가는 같은 해시다 — 코호트가 이어진다")
    void stable() {
        assertThat(new ExplorerHasher("salt-a").hash(EXPLORER_ID)).isEqualTo(new ExplorerHasher("salt-a").hash(EXPLORER_ID));
    }

    @Test
    @DisplayName("비밀값이 다르면 같은 탐험가도 다른 해시다 — 비밀값을 모르면 탐험가 id 로 맞춰 볼 수 없다")
    void saltMatters() {
        assertThat(new ExplorerHasher("salt-a").hash(EXPLORER_ID)).isNotEqualTo(new ExplorerHasher("salt-b").hash(EXPLORER_ID));
    }

    @Test
    @DisplayName("해시에는 탐험가 id 가 드러나지 않는다")
    void opaque() {
        assertThat(new ExplorerHasher("salt-a").hash(EXPLORER_ID).value()).hasSize(64).doesNotContain("0000000a");
    }

    @Test
    @DisplayName("표준 HMAC-SHA256 과 같은 값을 낸다(RFC 4231 시험 벡터 2)")
    void standardHmac() {
        assertThat(new ExplorerHasher("Jefe").fingerprint("what do ya want for nothing?"))
            .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    @DisplayName("비밀값이 비어 있으면 시작하지 않는다")
    void blankSalt() {
        assertThatThrownBy(() -> new ExplorerHasher(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
