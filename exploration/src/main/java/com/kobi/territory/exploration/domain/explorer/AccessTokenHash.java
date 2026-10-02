package com.kobi.territory.exploration.domain.explorer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 접근 토큰의 SHA-256 해시(소문자 hex 64자) — DB 에는 이것만 저장한다. 토큰이 충분히 길어 솔트는 두지 않는다. */
public record AccessTokenHash(String value) {

    public AccessTokenHash {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("토큰 해시 형식이 아닙니다");
    }

    public static AccessTokenHash of(AccessToken token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.value().getBytes(StandardCharsets.UTF_8));
            return new AccessTokenHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 없음", missing);
        }
    }
}
