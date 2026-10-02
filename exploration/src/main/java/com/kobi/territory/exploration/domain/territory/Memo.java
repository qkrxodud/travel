package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.exploration.domain.ExplorationError;

/** 한 줄 메모(≤40자, 코드 포인트 기준). null·공백은 빈 메모. 앞뒤 공백은 자른다. */
public record Memo(String value) {

    public static final int MAX_LENGTH = 40;
    public static final Memo EMPTY = new Memo("");

    public Memo {
        value = value == null ? "" : value.strip();
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw ExplorationError.MEMO_TOO_LONG.exception(MAX_LENGTH);
        }
    }

    public static Memo of(String value) {
        return value == null || value.isBlank() ? EMPTY : new Memo(value);
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }
}
