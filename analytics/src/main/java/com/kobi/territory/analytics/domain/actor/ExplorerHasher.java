package com.kobi.territory.analytics.domain.actor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 탐험가 id·사실 열쇠를 서버 비밀값(territory.analytics.salt)과 섞어 해시한다 — HMAC-SHA256(RFC 2104).
 * 같은 비밀값이면 재시작해도 같은 해시라 코호트가 이어지고, 비밀값을 바꾸면 그때부터 다른 사람으로 센다(운영 문서).
 * 도메인은 java 만 쓰므로 javax.crypto 대신 MessageDigest 로 HMAC 을 직접 계산한다.
 */
public final class ExplorerHasher {

    private static final int BLOCK = 64;

    private final byte[] innerPad = new byte[BLOCK];
    private final byte[] outerPad = new byte[BLOCK];

    public ExplorerHasher(String salt) {
        if (salt == null || salt.isBlank()) throw new IllegalArgumentException("territory.analytics.salt 가 비어 있다");
        byte[] key = salt.getBytes(StandardCharsets.UTF_8);
        if (key.length > BLOCK) key = sha256().digest(key);
        for (int i = 0; i < BLOCK; i++) {
            byte keyByte = i < key.length ? key[i] : 0;
            innerPad[i] = (byte) (keyByte ^ 0x36);
            outerPad[i] = (byte) (keyByte ^ 0x5c);
        }
    }

    public ExplorerHash hash(String explorerId) {
        return new ExplorerHash(hex(Objects.requireNonNull(explorerId, "explorerId")));
    }

    /** 같은 사실을 두 번 적지 않기 위한 지문(원문 대신 해시를 저장한다 — 사실 열쇠에 탐험가 id 가 들어 있어서). */
    public String fingerprint(String naturalKey) {
        return hex(Objects.requireNonNull(naturalKey, "naturalKey"));
    }

    private String hex(String message) {
        MessageDigest inner = sha256();
        inner.update(innerPad);
        byte[] innerHash = inner.digest(message.getBytes(StandardCharsets.UTF_8));
        MessageDigest outer = sha256();
        outer.update(outerPad);
        return HexFormat.of().formatHex(outer.digest(innerHash));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
