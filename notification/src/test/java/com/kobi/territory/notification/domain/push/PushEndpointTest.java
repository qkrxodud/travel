package com.kobi.territory.notification.domain.push;

import static com.kobi.territory.notification.domain.Fixtures.브라우저_키;
import static com.kobi.territory.notification.domain.Fixtures.인증_비밀;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("브라우저 구독 정보")
class PushEndpointTest {

    @Nested
    @DisplayName("구독 주소")
    class Address {

        @Test
        @DisplayName("같은 주소는 같은 기기 열쇠가 된다")
        void fingerprint() {
            PushEndpoint endpoint = PushEndpoint.of("https://fcm.googleapis.com/fcm/send/abc");

            assertThat(endpoint.fingerprint()).hasSize(64).isEqualTo(PushEndpoint.of(" https://fcm.googleapis.com/fcm/send/abc ").fingerprint());
            assertThat(endpoint.fingerprint()).isNotEqualTo(PushEndpoint.of("https://fcm.googleapis.com/fcm/send/abd").fingerprint());
        }

        @ParameterizedTest(name = "\"{0}\" 은 받지 않는다")
        @ValueSource(strings = {"", "fcm.googleapis.com/fcm/send/abc", "ftp://fcm.googleapis.com/x", "https://user:pw@fcm.googleapis.com/x",
            "https://fcm.googleapis.com/x#frag", "https://exa mple.com/x"})
        @DisplayName("주소 형식이 아니면 받지 않는다")
        void malformed(String value) {
            assertThatThrownBy(() -> PushEndpoint.of(value)).isInstanceOfSatisfying(TerritoryException.class,
                rejected -> assertThat(rejected.code()).isEqualTo("INVALID_PUSH_SUBSCRIPTION"));
        }

        @Test
        @DisplayName("너무 긴 주소는 받지 않는다")
        void tooLong() {
            assertThatThrownBy(() -> PushEndpoint.of("https://fcm.googleapis.com/" + "a".repeat(1100)))
                .isInstanceOf(TerritoryException.class);
        }

        @Test
        @DisplayName("기록에 남길 때 구독 고유 값은 감춘다")
        void hidesSecretPart() {
            assertThat(PushEndpoint.of("https://fcm.googleapis.com/fcm/send/secret-token").toString())
                .isEqualTo("https://fcm.googleapis.com/…").doesNotContain("secret-token");
        }
    }

    @Nested
    @DisplayName("암호화 키")
    class Keys {

        @Test
        @DisplayName("브라우저 공개 키 65바이트와 인증 비밀 16바이트를 받는다 — 표준 base64 로 와도 같은 값으로 맞춘다")
        void normalized() {
            String standard = 브라우저_키.replace('-', '+').replace('_', '/');

            assertThat(new DeviceKeys(standard, 인증_비밀 + "==").p256dh()).isEqualTo(브라우저_키);
        }

        @Test
        @DisplayName("길이가 다르거나 base64 가 아니면 받지 않는다")
        void malformed() {
            assertThatThrownBy(() -> new DeviceKeys(브라우저_키.substring(4), 인증_비밀)).isInstanceOf(TerritoryException.class);
            assertThatThrownBy(() -> new DeviceKeys(브라우저_키, "!!!")).isInstanceOf(TerritoryException.class);
            assertThatThrownBy(() -> new DeviceKeys(null, 인증_비밀)).isInstanceOf(TerritoryException.class);
        }

        @Test
        @DisplayName("기록에 남길 때 키는 감춘다")
        void hidden() {
            assertThat(new DeviceKeys(브라우저_키, 인증_비밀).toString()).doesNotContain(인증_비밀);
        }
    }
}
