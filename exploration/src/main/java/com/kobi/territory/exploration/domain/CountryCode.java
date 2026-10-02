package com.kobi.territory.exploration.domain;

/** ISO 3166-1 alpha-2 국가 코드. 지금은 KR만 운영한다. */
public record CountryCode(String value) {
    public static final CountryCode KR = new CountryCode("KR");

    public CountryCode {
        if (value == null || !value.matches("[A-Z]{2}")) throw ExplorationError.INVALID_MAP.exception("country=" + value);
    }
}
