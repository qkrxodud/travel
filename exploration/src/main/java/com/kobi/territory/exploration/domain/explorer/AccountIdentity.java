package com.kobi.territory.exploration.domain.explorer;

import com.kobi.territory.exploration.domain.ExplorationError;
import java.util.Locale;
import java.util.Objects;

/**
 * 외부 로그인이 확인해 준 신원 — (provider, subject) 가 계정의 유일 키다(구글은 OIDC sub). email 은 handle 자동 발급과 표시용.
 *
 * @param provider 로그인 제공자 등록 id(예: google)
 */
public record AccountIdentity(String provider, String subject, String email) {

    public static final int MAX_SUBJECT = 255;
    public static final int MAX_EMAIL = 320;

    public AccountIdentity {
        if (provider == null || !provider.matches("[a-z0-9-]{1,20}")) throw ExplorationError.ACCOUNT_INVALID.exception("provider");
        if (subject == null || subject.isBlank() || subject.length() > MAX_SUBJECT) {
            throw ExplorationError.ACCOUNT_INVALID.exception("subject");
        }
        Objects.requireNonNull(email, "email");
        email = email.strip().toLowerCase(Locale.ROOT);
        if (email.length() > MAX_EMAIL || !email.matches("[^@\\s]+@[^@\\s]+")) throw ExplorationError.ACCOUNT_INVALID.exception("email");
    }
}
