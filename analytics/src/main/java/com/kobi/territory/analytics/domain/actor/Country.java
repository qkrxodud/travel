package com.kobi.territory.analytics.domain.actor;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 나라(ISO 3166-1 alpha-2) — 앞단 프록시가 붙여 주는 국가 헤더(예: Cloudflare {@code CF-IPCountry})에서만 읽는다. IP 로 직접 찾지 않고
 * IP 는 저장하지 않는다. 모름({@code XX})·Tor({@code T1}) 은 없음으로.
 */
public record Country(String code) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}");

    public Country {
        if (code == null || !FORMAT.matcher(code).matches()) throw new IllegalArgumentException("나라 코드는 영문 대문자 2자");
    }

    public static Optional<Country> fromHeader(String header) {
        if (header == null) return Optional.empty();
        String code = header.trim().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(code).matches() || code.equals("XX") || code.equals("T1")) return Optional.empty();
        return Optional.of(new Country(code));
    }
}
