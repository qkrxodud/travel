package com.kobi.territory.common.model;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;
import java.util.regex.Pattern;

/** 지역(시·군·구) 코드. 형식은 {@code KR-11010} — 국가 코드 2자 + 행정 코드 5자리. */
public record RegionCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{2}-\\d{5}$");

    public RegionCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new TerritoryException("INVALID_REGION_CODE", ErrorKind.INVALID,
                "지역 코드 형식이 올바르지 않습니다(예: KR-11010): " + value);
        }
    }

    public static RegionCode of(String value) {
        return new RegionCode(value);
    }

    public String countryCode() {
        return value.substring(0, 2);
    }

    @Override
    public String toString() {
        return value;
    }
}
