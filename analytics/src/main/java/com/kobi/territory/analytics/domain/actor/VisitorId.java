package com.kobi.territory.analytics.domain.actor;

import com.kobi.territory.analytics.domain.AnalyticsError;
import java.util.regex.Pattern;

/**
 * 익명 방문 ID — 브라우저가 처음 열릴 때 만든 랜덤 값(localStorage, 쿠키 아님). 사람을 가리키지 않는 무작위 값이라 그대로 적는다.
 * 영문·숫자·{@code -}·{@code _} 8~64자(UUID 를 권장).
 */
public record VisitorId(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    public VisitorId {
        if (value == null || !FORMAT.matcher(value).matches()) throw AnalyticsError.INVALID_VISITOR_ID.exception();
    }

    public static VisitorId of(String value) {
        return new VisitorId(value);
    }
}
