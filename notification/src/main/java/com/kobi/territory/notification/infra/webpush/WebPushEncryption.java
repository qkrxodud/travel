package com.kobi.territory.notification.infra.webpush;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 웹 푸시 메시지 암호화 — RFC 8291(Message Encryption for Web Push) + RFC 8188 {@code aes128gcm} 레코드 하나. 받는 브라우저의
 * 공개 키(p256dh)·인증 비밀(auth)로 보낼 때마다 새 임시 키와 salt 를 만들어 감싼다. 푸시 서비스(FCM·Mozilla·Apple)는 내용을 읽지 못한다.
 * JDK 표준 암호만 쓴다(ECDH·HMAC-SHA256·AES-GCM). RFC 8291 부록 A 예시로 검증한다.
 */
final class WebPushEncryption {

    /** 레코드 크기(rs) — 메시지 하나가 레코드 하나에 들어가야 한다(평문 + 구분자 1 + 태그 16 ≤ rs). */
    static final int RECORD_SIZE = 4096;
    static final int MAX_PLAINTEXT = RECORD_SIZE - 17;

    private static final byte[] KEY_INFO = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

    private final SecureRandom random;

    WebPushEncryption(SecureRandom random) {
        this.random = random;
    }

    /** 보낼 본문(헤더 + 암호문). */
    byte[] encrypt(byte[] plaintext, byte[] userAgentPublic, byte[] authSecret) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(P256.PARAMS, random);
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            return encrypt(plaintext, userAgentPublic, authSecret, generator.generateKeyPair(), salt);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("임시 키를 만들 수 없다", unavailable);
        }
    }

    /** 임시 키·salt 를 정해서 암호화(시험 벡터 검증용). */
    static byte[] encrypt(byte[] plaintext, byte[] userAgentPublic, byte[] authSecret, KeyPair ephemeral, byte[] salt) {
        if (plaintext.length > MAX_PLAINTEXT) throw new IllegalArgumentException("푸시 본문이 너무 크다: " + plaintext.length);
        if (authSecret.length != 16) throw new IllegalArgumentException("auth 는 16바이트여야 한다");
        if (salt.length != 16) throw new IllegalArgumentException("salt 는 16바이트여야 한다");
        try {
            ECPublicKey receiver = P256.publicKey(userAgentPublic);
            byte[] serverPublic = P256.encode((ECPublicKey) ephemeral.getPublic());

            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(ephemeral.getPrivate());
            agreement.doPhase(receiver, true);
            byte[] sharedSecret = agreement.generateSecret();

            byte[] pseudoRandomKey = hmac(authSecret, sharedSecret);
            byte[] keyMaterial = hmac(pseudoRandomKey, concat(KEY_INFO, userAgentPublic, serverPublic, new byte[] {1}));
            byte[] contentKey = hmac(salt, keyMaterial);
            byte[] encryptionKey = Arrays.copyOf(hmac(contentKey, concat(CEK_INFO, new byte[] {1})), 16);
            byte[] nonce = Arrays.copyOf(hmac(contentKey, concat(NONCE_INFO, new byte[] {1})), 12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {2}));  // 0x02 = 마지막 레코드 구분자(채움 없음)

            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + serverPublic.length);
            header.put(salt).putInt(RECORD_SIZE).put((byte) serverPublic.length).put(serverPublic);
            return concat(header.array(), ciphertext);
        } catch (GeneralSecurityException failed) {
            throw new IllegalStateException("푸시 본문을 암호화할 수 없다", failed);
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        ByteBuffer joined = ByteBuffer.allocate(length);
        Arrays.stream(parts).forEach(joined::put);
        return joined.array();
    }
}
