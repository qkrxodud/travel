package com.kobi.territory.api.security;

import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * 인증 객체 → 계정 신원(provider, subject, email). 세션에 저장한 {@link AccountAuthenticationToken} 과, 스프링 OAuth2 로그인이
 * 만든 {@link OAuth2AuthenticationToken}(MockMvc oidcLogin()/oauth2Login() 도 이것) 둘 다 읽는다. 익명·그 밖의 인증은 빈 값.
 */
public record SessionAccount(String provider, String subject, String email) {

    public static Optional<SessionAccount> of(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        if (authentication instanceof AccountAuthenticationToken account) {
            return Optional.of(new SessionAccount(account.provider(), account.subject(), account.email()));
        }
        if (authentication instanceof OAuth2AuthenticationToken oauth) {
            String email = oauth.getPrincipal() instanceof OidcUser oidc ? oidc.getEmail()
                : oauth.getPrincipal().getAttribute("email");
            String subject = oauth.getPrincipal() instanceof OidcUser oidc ? oidc.getSubject() : oauth.getName();
            return Optional.of(new SessionAccount(oauth.getAuthorizedClientRegistrationId(), subject, email));
        }
        return Optional.empty();
    }
}
