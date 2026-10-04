package com.kobi.territory.analytics.domain.tracking;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 이벤트 필드 값의 종류. 자유 문장을 받지 않는다 — 짧은 식별자·오류 코드·작은 수·참거짓만(메모·이름 같은 글이 섞여 들어올 틈을 없앤다).
 * 값은 정규화한 문자열로 저장한다.
 */
public enum FieldType {
    /** 소문자 식별자: 영문 소문자·숫자·{@code _}·{@code -}, 1~32자(탭 이름·버튼 대상 등) */
    TOKEN {
        @Override
        Optional<String> normalize(Object raw) {
            return raw instanceof String text && TOKEN_FORMAT.matcher(text).matches() ? Optional.of(text) : Optional.empty();
        }
    },
    /** 서버 오류 코드: 영문 대문자로 시작, 대문자·숫자·{@code _} 2~64자 */
    ERROR_CODE {
        @Override
        Optional<String> normalize(Object raw) {
            return raw instanceof String text && CODE_FORMAT.matcher(text).matches() ? Optional.of(text) : Optional.empty();
        }
    },
    /** 지역·시·도 코드({@code KR-11010} 같은 형식) — 서버 사실에만 쓴다 */
    AREA_CODE {
        @Override
        Optional<String> normalize(Object raw) {
            return raw instanceof String text && AREA_FORMAT.matcher(text).matches() ? Optional.of(text) : Optional.empty();
        }
    },
    /** 0~1000 정수(온보딩 단계·개월 수 등) */
    SMALL_NUMBER {
        @Override
        Optional<String> normalize(Object raw) {
            if (!(raw instanceof Number number)) return Optional.empty();
            double value = number.doubleValue();
            if (value != Math.rint(value) || value < 0 || value > 1000) return Optional.empty();
            return Optional.of(Long.toString((long) value));
        }
    },
    /** 참·거짓 */
    FLAG {
        @Override
        Optional<String> normalize(Object raw) {
            return raw instanceof Boolean flag ? Optional.of(flag.toString()) : Optional.empty();
        }
    };

    private static final Pattern TOKEN_FORMAT = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");
    private static final Pattern CODE_FORMAT = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final Pattern AREA_FORMAT = Pattern.compile("[A-Z]{2}-[0-9]{2,5}");

    /** 형식에 맞으면 저장할 문자열, 아니면 빈 값. */
    abstract Optional<String> normalize(Object raw);
}
