package com.kobi.territory.catalog.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 한국관광공사 TourAPI 연결 설정(13s단계, territory.tourapi.* — 조립 모듈이 바인딩해 넘긴다). 서비스 키는 선택 값이다: 없으면 아무 호출도 하지 않고
 * 계절 회차는 기본 목록(AI 추정)을 그대로 쓴다. 키는 toString·로그·오류 메시지에 남기지 않는다.
 *
 * @param serviceKey     공공데이터포털 인증키(환경변수 TOURAPI_SERVICE_KEY). 디코딩 키(원문)·인코딩 키(%xx) 어느 쪽이든 된다
 * @param baseUrl        서비스 주소(기본 https://apis.data.go.kr/B551011/KorService2 — 개발 가짜 서버로 바꿀 수 있다)
 * @param mobileApp      요청 변수 MobileApp(서비스명)
 * @param timeout        요청 하나의 연결·응답 시간 제한
 * @param retries        연결 실패·5xx 때 다시 시도하는 횟수(키 거절·한도 초과는 다시 하지 않는다)
 * @param retryBackoff   첫 재시도 대기(이후 두 배씩)
 * @param pageSize       한 쪽 행 수(numOfRows) — 크게 잡아 호출 수를 줄인다(회차 하나 = 보통 한 쪽)
 * @param maxPages       한 번 모을 때 읽는 최대 쪽 수
 * @param dailyCallLimit 이 서버가 하루(서비스 시간대 날짜)에 하는 최대 호출 수 — 1 ~ {@value #MAX_DAILY_CALL_LIMIT}(기관 한도 하루 1,000건 미만보다 낮게,
 *                       벗어나면 기동 실패). 재시도도 한 번으로 센다.
 *                       세는 값은 DB(tourapi_usage)라 재기동해도 이어진다
 */
public record TourApiSettings(String serviceKey, String baseUrl, String mobileApp, Duration timeout, int retries, Duration retryBackoff,
                              int pageSize, int maxPages, int dailyCallLimit) {

    public TourApiSettings {
        serviceKey = serviceKey == null ? "" : serviceKey.trim();
        Objects.requireNonNull(baseUrl, "baseUrl");
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        Objects.requireNonNull(mobileApp, "mobileApp");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(retryBackoff, "retryBackoff");
        if (retries < 0 || pageSize < 1 || maxPages < 1) {
            throw new IllegalArgumentException("TourAPI 설정값 범위 오류(retries ≥ 0, page-size ≥ 1, max-pages ≥ 1)");
        }
        if (dailyCallLimit < 1 || dailyCallLimit > MAX_DAILY_CALL_LIMIT) {
            throw new IllegalArgumentException("territory.tourapi.daily-call-limit 는 1 ~ " + MAX_DAILY_CALL_LIMIT
                + " 이어야 한다(기관 일일 한도 1,000건 미만보다 낮게)");
        }
    }

    /** 하루 호출 상한의 최댓값 — 기관 한도(1,000건 미만)에 여유를 둔다. */
    public static final int MAX_DAILY_CALL_LIMIT = 900;

    private static final Pattern KEY_CHARACTERS = Pattern.compile("^[\\x21-\\x7E]+$");
    private static final Pattern BROKEN_ESCAPE = Pattern.compile("%(?![0-9A-Fa-f]{2})");

    /** 서비스 키가 있고 키·주소 형식이 맞아 연동이 켜졌는지. */
    public boolean configured() {
        return hasKey() && problem().isEmpty();
    }

    /** 서비스 키를 넣었는지(형식과 무관). */
    public boolean hasKey() {
        return !serviceKey.isEmpty();
    }

    /**
     * 키를 넣었는데 형식이 틀린 까닭(연동을 끈다). 값은 넣지 않는다 — 로그·관리자 화면에 그대로 써도 키가 새지 않는다. 형식이 맞거나 키가 없으면 빈 값.
     * 키: 공백·제어 문자·한글 없이 보이는 ASCII 만, {@code %} 는 뒤에 16진 두 자리(잘린 인코딩 키 거절). 주소: 공백 없는 http/https 절대 주소(호스트 있음,
     * 쿼리 없음).
     */
    public Optional<String> problem() {
        if (!hasKey()) return Optional.empty();
        if (!KEY_CHARACTERS.matcher(serviceKey).matches() || BROKEN_ESCAPE.matcher(serviceKey).find()) {
            return Optional.of("서비스 키 형식 오류(공백·제어 문자 또는 잘린 %인코딩) — 공공데이터포털의 일반 인증키를 그대로 붙여 넣으세요");
        }
        try {
            URI uri = new URI(baseUrl);
            boolean web = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
            if (!web || uri.getHost() == null || uri.getRawQuery() != null) throw new URISyntaxException(baseUrl, "http(s) 절대 주소 아님");
        } catch (URISyntaxException malformed) {
            return Optional.of("territory.tourapi.base-url 형식 오류(공백 없는 http/https 절대 주소여야 함)");
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "TourApiSettings[serviceKey=" + (hasKey() ? "***" : "(없음)") + ", baseUrl=" + baseUrl + ", pageSize=" + pageSize
            + ", maxPages=" + maxPages + ", dailyCallLimit=" + dailyCallLimit + "]";
    }
}
