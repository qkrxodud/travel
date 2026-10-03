package com.kobi.territory.api.security;

import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * 로그인 세션의 인증 — 계정 신원(provider, subject)만 기억한다(explorerId 는 요청마다 계정으로 찾는다: 병합·연결 변화를 바로 반영).
 * 구글 OIDC 성공·local /dev/login 모두 이것으로 세션에 저장한다(권한 없음 — 인가는 각 유스케이스가 한다).
 */
public final class AccountAuthenticationToken extends AbstractAuthenticationToken {

    private final String provider;
    private final String subject;
    private final String email;

    public AccountAuthenticationToken(String provider, String subject, String email) {
        super(List.of());
        this.provider = Objects.requireNonNull(provider, "provider");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.email = email;
        setAuthenticated(true);
    }

    public String provider() { return provider; }
    public String subject() { return subject; }
    public String email() { return email; }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return provider + ":" + subject;
    }
}
