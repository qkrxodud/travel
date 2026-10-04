package com.kobi.territory.notification.infra.webpush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("알림 서버 신원(VAPID)")
class VapidKeysTest {

    /** application-local.yml 의 시험용 키 — doc/operations.md 의 openssl 명령으로 만든 값. */
    static final String PUBLIC = "BL4nehRk6sf8mdeCUNdZYgFJV8YQKhg_DBB2UPhEBtJhpCGph6jaB7u4cWesO4IFwVwnsate9RFv1cnqDuuXjgU";
    static final String PRIVATE = "_rA6y0IIhChRfj6xo4JxQ9rYj8bkwc2pIuzrjvKfyY8";
    private static final String OTHER_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";

    @Nested
    @DisplayName("키를 읽을 때")
    class Load {

        @Test
        @DisplayName("운영 문서의 명령으로 만든 키 쌍을 그대로 읽는다")
        void opensslRecipe() {
            assertThat(VapidKeys.of(PUBLIC, PRIVATE, "mailto:ops@territory.kr").publicKey()).isEqualTo(PUBLIC);
        }

        @Test
        @DisplayName("공개 키와 개인 키가 한 쌍이 아니면 시작하지 않는다")
        void mismatchedPair() {
            assertThatThrownBy(() -> VapidKeys.of(PUBLIC, OTHER_PRIVATE, "mailto:ops@territory.kr"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("한 쌍");
        }

        @Test
        @DisplayName("형식이 틀린 키는 시작하지 않고, 오류에 개인 키를 적지 않는다")
        void malformed() {
            assertThatThrownBy(() -> VapidKeys.of(PUBLIC, "short", "mailto:ops@territory.kr")).isInstanceOf(IllegalArgumentException.class)
                .satisfies(rejected -> assertThat(rejected.getMessage()).doesNotContain(PRIVATE));
            assertThatThrownBy(() -> VapidKeys.of("", PRIVATE, "mailto:ops@territory.kr")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("연락처는 mailto 주소나 https 주소여야 한다")
        void subject() {
            assertThatThrownBy(() -> VapidKeys.of(PUBLIC, PRIVATE, "ops@territory.kr")).isInstanceOf(IllegalArgumentException.class);
            assertThat(VapidKeys.of(PUBLIC, PRIVATE, "https://territory.kr").publicKey()).isEqualTo(PUBLIC);
        }

        @Test
        @DisplayName("기록에 남길 때 개인 키는 감춘다")
        void hidden() {
            assertThat(VapidKeys.of(PUBLIC, PRIVATE, "mailto:ops@territory.kr").toString()).doesNotContain(PRIVATE);
        }
    }

    @Nested
    @DisplayName("알림 서비스에 신원을 밝힐 때")
    class Authorization {

        @Test
        @DisplayName("구독 주소의 출처·만료·연락처를 담은 서명 토큰과 공개 키를 보낸다 — 공개 키로 서명을 확인할 수 있다")
        void signedToken() {
            VapidKeys keys = VapidKeys.of(PUBLIC, PRIVATE, "mailto:ops@territory.kr");
            Instant now = Instant.parse("2026-10-05T00:00:00Z");

            String header = keys.authorization(URI.create("https://fcm.googleapis.com/fcm/send/abc"), now, Duration.ofHours(12));

            assertThat(header).startsWith("vapid t=").endsWith(", k=" + PUBLIC);
            String token = header.substring("vapid t=".length(), header.indexOf(", k="));
            String[] parts = token.split("\\.");
            String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"")
                .contains("\"exp\":" + now.plus(Duration.ofHours(12)).getEpochSecond()).contains("\"sub\":\"mailto:ops@territory.kr\"");
            assertThat(keys.verify((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII), Base64.getUrlDecoder().decode(parts[2])))
                .isTrue();
        }
    }
}
