package com.kobi.territory.common.model;

import java.util.Objects;
import java.util.UUID;

/** 탐험가 식별자(UUID 문자열). 1~3단계는 익명 탐험가도 같은 식별자를 쓴다. */
public record ExplorerId(String value) {

    public ExplorerId {
        Objects.requireNonNull(value, "explorerId");
        try {
            value = UUID.fromString(value).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("explorerId 형식이 아닙니다: " + value);
        }
    }

    public static ExplorerId newId() {
        return new ExplorerId(UUID.randomUUID().toString());
    }

    public static ExplorerId of(String value) {
        return new ExplorerId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
