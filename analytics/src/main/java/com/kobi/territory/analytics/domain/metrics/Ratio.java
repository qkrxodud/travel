package com.kobi.territory.analytics.domain.metrics;

/** 비율(0~1, 소수 넷째 자리 반올림). 분모가 0이면 없음(null) — 0% 와 "셀 수 없음"을 구분한다. */
final class Ratio {

    private Ratio() {}

    static Double of(long part, long whole) {
        if (whole <= 0) return null;
        return Math.round((double) part / whole * 10_000d) / 10_000d;
    }
}
