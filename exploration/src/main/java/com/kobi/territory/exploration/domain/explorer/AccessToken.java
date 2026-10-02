package com.kobi.territory.exploration.domain.explorer;

import java.util.Base64;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * 탐험가 비밀 접근 토큰(결정 2) — 발급 응답에만 한 번 실리고 저장은 해시({@link AccessTokenHash})로만 한다.
 * 32바이트 난수의 base64url(43자). explorerId 는 공개돼도 되지만 이 값은 비밀이다.
 */
public record AccessToken(String value) {

    public static final int BYTES = 32;
    public static final int MAX_LENGTH = 128;

    public AccessToken {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("접근 토큰 형식이 아닙니다");
        }
    }

    /** 난수원(application 이 SecureRandom 을 넘긴다)으로 새 토큰을 만든다. */
    public static AccessToken generate(RandomGenerator random) {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return new AccessToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** 요청 헤더 값 → 토큰. 비었거나 형식이 틀리면 빈 값(인증 실패로 다룬다). */
    public static Optional<AccessToken> parse(String raw) {
        return raw == null || raw.isBlank() || raw.strip().length() > MAX_LENGTH
            ? Optional.empty() : Optional.of(new AccessToken(raw.strip()));
    }

    public AccessTokenHash hash() {
        return AccessTokenHash.of(this);
    }

    @Override
    public String toString() {
        return "AccessToken[***]";
    }
}
