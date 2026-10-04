package com.kobi.territory.notification.infra.webpush;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/**
 * VAPID(RFC 8292) 서버 신원 — 푸시 서비스에 "이 구독을 만든 그 서버"임을 ES256 JWT 로 증명한다. 키는 웹 푸시 관례 형식(공개 키 = 비압축 점
 * 65바이트, 개인 키 = 스칼라 32바이트, 모두 base64url)으로 설정에서 받는다. 만들 때 키 쌍이 맞는지(서명 → 검증)와 subject 형식
 * ({@code mailto:} 또는 {@code https://})을 확인한다 — 틀리면 기동하지 않는다. 개인 키는 toString·로그에 남기지 않는다.
 */
final class VapidKeys {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER = ENCODER.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));

    private final ECPublicKey publicKey;
    private final ECPrivateKey privateKey;
    private final String encodedPublicKey;
    private final String subject;

    private VapidKeys(ECPublicKey publicKey, ECPrivateKey privateKey, String subject) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.encodedPublicKey = ENCODER.encodeToString(P256.encode(publicKey));
        this.subject = subject;
    }

    /** 설정값 → 키. 형식이 틀리거나 쌍이 맞지 않거나 subject 가 틀리면 IllegalArgumentException(값은 메시지에 넣지 않는다). */
    static VapidKeys of(String publicKey, String privateKey, String subject) {
        if (publicKey == null || publicKey.isBlank()) throw new IllegalArgumentException("VAPID 공개 키가 비어 있다");
        if (privateKey == null || privateKey.isBlank()) throw new IllegalArgumentException("VAPID 개인 키가 비어 있다");
        String contact = Objects.requireNonNullElse(subject, "").trim();
        String lower = contact.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("mailto:") && contact.length() > 8 && contact.contains("@")) && !lower.startsWith("https://")) {
            throw new IllegalArgumentException("VAPID subject 는 mailto:주소 또는 https:// 주소여야 한다");
        }
        ECPublicKey publicPart;
        ECPrivateKey privatePart;
        try {
            publicPart = P256.publicKey(DECODER.decode(publicKey.trim()));
            privatePart = P256.privateKey(DECODER.decode(privateKey.trim()));
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("VAPID 키 형식이 틀렸다(공개 키 65바이트·개인 키 32바이트 base64url): " + malformed.getMessage());
        }
        VapidKeys keys = new VapidKeys(publicPart, privatePart, contact);
        keys.requirePair();
        return keys;
    }

    /** 브라우저 구독(applicationServerKey)에 넘기는 공개 키(base64url). */
    String publicKey() {
        return encodedPublicKey;
    }

    /**
     * 푸시 서비스에 보낼 Authorization 헤더 값({@code vapid t=JWT, k=공개키}). aud = 구독 주소의 출처(스킴 + 호스트[+ 포트]), exp ≤ 24시간.
     */
    String authorization(URI endpoint, Instant now, Duration lifetime) {
        String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        long expires = now.plus(lifetime).getEpochSecond();
        String claims = "{\"aud\":\"" + json(audience) + "\",\"exp\":" + expires + ",\"sub\":\"" + json(subject) + "\"}";
        String unsigned = HEADER + "." + ENCODER.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        return "vapid t=" + unsigned + "." + ENCODER.encodeToString(sign(unsigned.getBytes(StandardCharsets.US_ASCII)))
            + ", k=" + encodedPublicKey;
    }

    /** ES256 서명(JWS 형식 — r‖s 64바이트). */
    byte[] sign(byte[] data) {
        try {
            Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
            signer.initSign(privateKey);
            signer.update(data);
            return signer.sign();
        } catch (GeneralSecurityException failed) {
            throw new IllegalStateException("VAPID 서명을 만들 수 없다", failed);
        }
    }

    boolean verify(byte[] data, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(signature);
        } catch (GeneralSecurityException failed) {
            return false;
        }
    }

    private void requirePair() {
        byte[] probe = "territory-vapid-pair-check".getBytes(StandardCharsets.US_ASCII);
        if (!verify(probe, sign(probe))) throw new IllegalArgumentException("VAPID 공개 키와 개인 키가 한 쌍이 아니다");
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String toString() {
        return "VapidKeys[public=" + encodedPublicKey + ", private=****, subject=" + subject + "]";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VapidKeys keys && Arrays.equals(P256.encode(publicKey), P256.encode(keys.publicKey));
    }

    @Override
    public int hashCode() {
        return encodedPublicKey.hashCode();
    }
}
