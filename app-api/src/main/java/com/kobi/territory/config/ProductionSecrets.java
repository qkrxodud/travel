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
 * <ul>
 *   <li>10단계 QA r2 P3-a: 분석 비밀값이 미스터리 비밀값과 같으면 기동하지 않는다(하나가 새면 다른 쪽도 샌다 — 서로 다른 강한 값)</li>
 *   <li>12단계: VAPID 키가 비었거나 로컬 시험용 키(application-local.yml — 공개된 값)면, subject 가 로컬 예시 주소면 기동하지 않는다.
 *       키 형식·한 쌍 여부는 웹 푸시 구현이 모든 프로파일에서 기동할 때 확인한다</li>
 * </ul>
 */
@Component
@Profile("prod")
public class ProductionSecrets {

    static final int MIN_LENGTH = 16;
    private static final List<String> PLACEHOLDERS = List.of("change-me", "changeme", "change_me", "local-", "example", "password",
        "secret", "todo");

    /** application-local.yml 의 VAPID 시험용 공개 키(공개된 값 — 운영에 쓰면 누구나 그 서버인 척 보낼 수 있다). */
    private static final List<String> SUBJECT_PLACEHOLDERS = List.of("territory.local", "example", "change-me", "changeme");

    static final String LOCAL_VAPID_PUBLIC_KEY = "BL4nehRk6sf8mdeCUNdZYgFJV8YQKhg_DBB2UPhEBtJhpCGph6jaB7u4cWesO4IFwVwnsate9RFv1cnqDuuXjgU";

    public ProductionSecrets(@Value("${territory.admin.token}") String adminToken,
                             @Value("${territory.mystery.salt}") String mysterySalt,
                             @Value("${territory.analytics.salt}") String analyticsSalt,
                             @Value("${territory.push.vapid.public-key}") String vapidPublicKey,
                             @Value("${territory.push.vapid.subject}") String vapidSubject) {
        require("TERRITORY_ADMIN_TOKEN", adminToken);
        require("TERRITORY_MYSTERY_SALT", mysterySalt);
        require("TERRITORY_ANALYTICS_SALT", analyticsSalt);
        if (analyticsSalt.trim().equals(mysterySalt.trim())) {
            throw new IllegalStateException("TERRITORY_ANALYTICS_SALT 가 TERRITORY_MYSTERY_SALT 와 같습니다 — 서로 다른 강한 랜덤 값으로 정하세요"
                + "(분석 값은 한 번 정하면 바꾸지 않으므로 미스터리 값을 바꾸세요).");
        }
        if (vapidPublicKey == null || vapidPublicKey.isBlank() || vapidPublicKey.trim().equals(LOCAL_VAPID_PUBLIC_KEY)) {
            throw new IllegalStateException("TERRITORY_VAPID_PUBLIC_KEY·TERRITORY_VAPID_PRIVATE_KEY 가 비었거나 로컬 시험용 키입니다 — "
                + "운영 키 쌍을 새로 만드세요(doc/operations.md 12단계).");
        }
        String subject = vapidSubject == null ? "" : vapidSubject.toLowerCase(Locale.ROOT);
        if (subject.isBlank() || SUBJECT_PLACEHOLDERS.stream().anyMatch(subject::contains)) {
            throw new IllegalStateException("TERRITORY_VAPID_SUBJECT 가 비었거나 예시 주소입니다 — 운영 연락처(mailto: 또는 https://)로 바꾸세요.");
        }
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
