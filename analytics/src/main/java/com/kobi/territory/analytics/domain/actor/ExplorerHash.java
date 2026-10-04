package com.kobi.territory.analytics.domain.actor;

import java.util.regex.Pattern;

/**
 * 탐험가 id 를 서버 비밀값과 섞어 만든 해시(HMAC-SHA256, 소문자 16진수 64자). 분석 저장소에는 탐험가 id 대신 이것만 남는다 —
 * 비밀값을 모르면 탐험가 id 로 되돌리거나 맞춰 볼 수 없다.
 */
public record ExplorerHash(String value) {

    private static final Pattern FORMAT = Pattern.compile("[0-9a-f]{64}");

    public ExplorerHash {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("탐험가 해시는 16진수 64자여야 한다");
        }
    }
}
