package com.kobi.territory.notification.infra.webpush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("푸시 메시지 암호화")
class WebPushEncryptionTest {

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    // RFC 8291 부록 A 의 예시 값
    private static final String PLAINTEXT = "V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24";
    private static final String SERVER_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String SERVER_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String BROWSER_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String BROWSER_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String MESSAGE = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Nested
    @DisplayName("표준 예시와 견주면")
    class StandardExample {

        @Test
        @DisplayName("같은 키와 salt 로 표준 문서의 메시지와 한 바이트도 다르지 않게 만든다")
        void matchesRfc8291() {
            KeyPair server = new KeyPair(P256.publicKey(DECODER.decode(SERVER_PUBLIC)), P256.privateKey(DECODER.decode(SERVER_PRIVATE)));

            byte[] message = WebPushEncryption.encrypt(DECODER.decode(PLAINTEXT), DECODER.decode(BROWSER_PUBLIC), DECODER.decode(AUTH),
                server, DECODER.decode(SALT));

            assertThat(ENCODER.encodeToString(message)).isEqualTo(MESSAGE);
        }
    }

    @Nested
    @DisplayName("브라우저가 받으면")
    class BrowserReceives {

        @Test
        @DisplayName("구독할 때 만든 비밀로 원래 내용을 다시 읽을 수 있다")
        void roundTrip() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(P256.PARAMS);
            KeyPair browser = generator.generateKeyPair();
            byte[] auth = new byte[16];
            new SecureRandom().nextBytes(auth);
            byte[] browserPublic = P256.encode((ECPublicKey) browser.getPublic());
            String payload = "{\"kind\":\"mystery\",\"title\":\"이번 주 미스터리 지역\"}";

            byte[] message = new WebPushEncryption(new SecureRandom()).encrypt(payload.getBytes(StandardCharsets.UTF_8), browserPublic, auth);

            assertThat(new String(decrypt(message, (ECPrivateKey) browser.getPrivate(), browserPublic, auth), StandardCharsets.UTF_8))
                .isEqualTo(payload);
        }

        @Test
        @DisplayName("보낼 때마다 다른 임시 키를 써서 같은 내용도 다르게 보인다")
        void freshKeysEachTime() {
            WebPushEncryption encryption = new WebPushEncryption(new SecureRandom());
            byte[] plaintext = "같은 내용".getBytes(StandardCharsets.UTF_8);

            byte[] first = encryption.encrypt(plaintext, DECODER.decode(BROWSER_PUBLIC), DECODER.decode(AUTH));
            byte[] second = encryption.encrypt(plaintext, DECODER.decode(BROWSER_PUBLIC), DECODER.decode(AUTH));

            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("받는 쪽 키가 이상하면")
    class BadReceiver {

        @Test
        @DisplayName("곡선 위의 점이 아닌 공개 키로는 보내지 않는다")
        void notOnCurve() {
            byte[] forged = DECODER.decode(BROWSER_PUBLIC);
            forged[64] ^= 1;

            assertThatThrownBy(() -> new WebPushEncryption(new SecureRandom()).encrypt(new byte[] {1}, forged, DECODER.decode(AUTH)))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("레코드 하나에 들어가지 않는 큰 내용은 보내지 않는다")
        void tooLarge() {
            byte[] huge = new byte[WebPushEncryption.MAX_PLAINTEXT + 1];

            assertThatThrownBy(() -> new WebPushEncryption(new SecureRandom()).encrypt(huge, DECODER.decode(BROWSER_PUBLIC),
                DECODER.decode(AUTH))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** 브라우저 쪽 복호화(RFC 8291 §3.4 를 그대로 — 시험 전용). */
    static byte[] decrypt(byte[] message, ECPrivateKey browserPrivate, byte[] browserPublic, byte[] auth) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(message);
        byte[] salt = new byte[16];
        buffer.get(salt);
        buffer.getInt();
        byte[] serverPublic = new byte[buffer.get()];
        buffer.get(serverPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browserPrivate);
        agreement.doPhase(P256.publicKey(serverPublic), true);
        byte[] shared = agreement.generateSecret();
        byte[] prkKey = hmac(auth, shared);
        byte[] ikm = hmac(prkKey, concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), browserPublic, serverPublic, new byte[] {1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        int end = padded.length - 1;
        while (padded[end] == 0) end--;
        assertThat(padded[end]).isEqualTo((byte) 2);
        return Arrays.copyOf(padded, end);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteBuffer joined = ByteBuffer.allocate(Arrays.stream(parts).mapToInt(part -> part.length).sum());
        Arrays.stream(parts).forEach(joined::put);
        return joined.array();
    }
}
