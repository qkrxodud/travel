package com.kobi.territory.config;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 운영(prod) 비밀값 강도 확인(10단계 QA P3-5) — 관리자 토큰·미스터리 비밀값·분석 비밀값이 비었거나 짧거나(16자 미만) 자리표시자
 * ({@code change-me}·{@code local-…} 등 .env.example·local 기본값)면 기동하지 않는다. 값 자체는 로그에 남기지 않는다.
 */
@Component
@Profile("prod")
public class ProductionSecrets {

    static final int MIN_LENGTH = 16;
    private static final List<String> PLACEHOLDERS = List.of("change-me", "changeme", "change_me", "local-", "example", "password",
        "secret", "todo");

    public ProductionSecrets(@Value("${territory.admin.token}") String adminToken,
                             @Value("${territory.mystery.salt}") String mysterySalt,
                             @Value("${territory.analytics.salt}") String analyticsSalt) {
        require("TERRITORY_ADMIN_TOKEN", adminToken);
        require("TERRITORY_MYSTERY_SALT", mysterySalt);
        require("TERRITORY_ANALYTICS_SALT", analyticsSalt);
    }

    /** 약한 값이면 어느 환경변수인지와 이유만 담아 거절한다. */
    static void require(String name, String value) {
        weakness(value).ifPresent(reason -> {
            throw new IllegalStateException(name + " 이 운영에 쓰기에 약합니다(" + reason + ") — 강한 랜덤 값으로 바꾸세요"
                + "(예: openssl rand -base64 48 | tr -d '/+=\\n' | cut -c1-40).");
        });
    }

    static Optional<String> weakness(String value) {
        if (value == null || value.isBlank()) return Optional.of("비어 있음");
        if (value.trim().length() < MIN_LENGTH) return Optional.of(MIN_LENGTH + "자 미만");
        String lower = value.toLowerCase(Locale.ROOT);
        return PLACEHOLDERS.stream().filter(lower::contains).findFirst().map(word -> "자리표시자 '" + word + "' 포함");
    }
}
