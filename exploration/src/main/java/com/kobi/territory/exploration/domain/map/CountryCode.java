package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.exploration.domain.ExplorationError;
import java.util.Locale;

/** ISO 3166-1 alpha-2 국가 코드. 지금은 KR만 운영한다. */
public record CountryCode(String value) {
    public static final CountryCode KR = new CountryCode("KR");

    public CountryCode {
        if (value == null || !value.matches("[A-Z]{2}")) throw ExplorationError.INVALID_MAP.exception("country=" + value);
    }

    /** 요청 값(생략 가능) → 국가 코드. 생략하면 KR. */
    public static CountryCode orKorea(String raw) {
        return raw == null || raw.isBlank() ? KR : new CountryCode(raw.strip().toUpperCase(Locale.ROOT));
    }
}
