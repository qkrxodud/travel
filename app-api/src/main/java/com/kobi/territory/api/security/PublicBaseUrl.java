package com.kobi.territory.api.security;

import java.net.URI;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 공개 기준 주소(설정값 {@code territory.public-base-url}, 예: https://territory.kr) — 로그인 리디렉트(OAuth redirect_uri)처럼 밖으로
 * 나가는 절대 주소는 요청 Host 헤더가 아니라 이 값으로 만든다(QA P3-2: Host 헤더 주입·프록시 뒤 http 문제). 파트 B 의 og:image·og:url 도
 * 같은 키를 쓴다. prod 는 환경변수 TERRITORY_PUBLIC_BASE_URL 필수, local 은 비워 두면 요청 기준(개발 편의).
 */
@Component
public class PublicBaseUrl {

    private final String value;

    public PublicBaseUrl(@Value("${territory.public-base-url:}") String configured) {
        String trimmed = configured == null ? "" : configured.strip();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (!trimmed.isEmpty()) {
            URI uri = URI.create(trimmed);
            if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalStateException("territory.public-base-url 은 http(s)://호스트 형식이어야 합니다: " + configured);
            }
        }
        this.value = trimmed;
    }

    /** 설정된 기준 주소(끝 / 없음). 비어 있으면 빈 값. */
    public Optional<String> value() {
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    /** https 로 서비스하는지(쿠키 Secure 판단). */
    public boolean secure() {
        return value.startsWith("https://");
    }

    /** OAuth redirect_uri 템플릿 — 설정이 있으면 그 주소, 없으면 스프링 기본 {baseUrl}. */
    String redirectUriTemplate() {
        return value().orElse("{baseUrl}") + "/login/oauth2/code/{registrationId}";
    }
}
