package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.exploration.domain.ExplorationError;
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

    public static InviteCode generate(RandomGenerator random) {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return new InviteCode(sb.toString());
    }
}
