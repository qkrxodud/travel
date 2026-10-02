package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.exploration.domain.ExplorationError;
import java.util.Locale;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** 초대코드(8자, 헷갈리는 0/O/1/I 제외). 지도당 하나·전체 유일(DB UNIQUE), 재발급 가능. */
public record InviteCode(String value) {

    public static final int LENGTH = 8;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public InviteCode {
        if (value == null || value.length() != LENGTH || !value.chars().allMatch(character -> ALPHABET.indexOf(character) >= 0)) {
            throw ExplorationError.INVALID_MAP.exception("inviteCode=" + value);
        }
    }

    /** 사용자가 입력한 코드(앞뒤 공백·소문자 허용) → 초대코드. 형식이 틀리면 빈 값. */
    public static Optional<InviteCode> parse(String raw) {
        if (raw == null) return Optional.empty();
        String normalized = raw.strip().toUpperCase(Locale.ROOT);
        return normalized.length() == LENGTH && normalized.chars().allMatch(character -> ALPHABET.indexOf(character) >= 0)
            ? Optional.of(new InviteCode(normalized)) : Optional.empty();
    }

    public static InviteCode generate(RandomGenerator random) {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return new InviteCode(sb.toString());
    }
}
