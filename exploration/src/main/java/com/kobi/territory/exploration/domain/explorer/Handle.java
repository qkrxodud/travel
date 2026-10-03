package com.kobi.territory.exploration.domain.explorer;

import com.kobi.territory.exploration.domain.ExplorationError;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * 공개 프로필 핸들(4단계, {@code /u/{handle}}). 소문자·숫자·밑줄·하이픈 3~20자, 첫 글자는 영숫자. 금칙어(경로·운영 용어)는 쓸 수 없다.
 * 저장은 늘 소문자 — 조회도 {@link #parse}로 정규화해 비교한다.
 */
public record Handle(String value) {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 20;
    private static final String PATTERN = "[a-z0-9][a-z0-9_-]{" + (MIN_LENGTH - 1) + "," + (MAX_LENGTH - 1) + "}";
    /** 금칙어(최소) — 경로·운영 용어와 헷갈리는 이름. */
    static final Set<String> RESERVED = Set.of("admin", "administrator", "root", "api", "dev", "me", "u", "login", "logout",
        "auth", "oauth", "system", "support", "help", "territory", "official", "null", "undefined", "anonymous", "explorer");
    static final String RANDOM_PREFIX = "explorer-";
    private static final String RANDOM_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"; // 헷갈리는 l·o·0·1 제외
    private static final int RANDOM_SHORT = 4;
    private static final int RANDOM_LONG = 6;
    private static final int RANDOM_ATTEMPTS = 5;

    public Handle {
        if (value == null || !value.matches(PATTERN)) {
            throw ExplorationError.HANDLE_INVALID.exception(MIN_LENGTH, MAX_LENGTH);
        }
        if (RESERVED.contains(value)) throw ExplorationError.HANDLE_RESERVED.exception(value);
    }

    /** 사용자 입력 → 핸들(앞뒤 공백·'@' 제거, 소문자). 형식이 틀리면 HANDLE_INVALID, 금칙어면 HANDLE_RESERVED. */
    public static Handle of(String raw) {
        return new Handle(normalize(raw));
    }

    /** 조회용 — 형식이 틀리거나 금칙어면 빈 값(없는 핸들로 다룬다). */
    public static Optional<Handle> parse(String raw) {
        String normalized = normalize(raw);
        return normalized.matches(PATTERN) && !RESERVED.contains(normalized) ? Optional.of(new Handle(normalized)) : Optional.empty();
    }

    /**
     * 최초 로그인 자동 발급(사용자 결정 Q1 — 이메일과 무관한 랜덤 handle): {@code explorer-xxxx}(소문자·숫자 4자). 쓰였거나 예약된
     * handle 이면 다시 뽑고, 몇 번 겹치면 6자로 늘린다. taken 은 저장소 확인(동시 발급은 DB UNIQUE 가 막고 호출자가 재시도).
     * 사용자는 프로필 탭에서 바꿀 수 있다.
     */
    public static Handle random(RandomGenerator random, Predicate<Handle> taken) {
        for (int attempt = 0; ; attempt++) {
            int length = attempt < RANDOM_ATTEMPTS ? RANDOM_SHORT : RANDOM_LONG;
            StringBuilder suffix = new StringBuilder(length);
            for (int i = 0; i < length; i++) suffix.append(RANDOM_ALPHABET.charAt(random.nextInt(RANDOM_ALPHABET.length())));
            Handle handle = new Handle(RANDOM_PREFIX + suffix);
            if (!taken.test(handle)) return handle;
        }
    }

    private static String normalize(String raw) {
        String stripped = raw == null ? "" : raw.strip();
        if (stripped.startsWith("@")) stripped = stripped.substring(1);
        return stripped.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value;
    }
}
