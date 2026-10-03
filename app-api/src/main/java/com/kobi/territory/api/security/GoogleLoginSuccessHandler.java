package com.kobi.territory.api.security;

import com.kobi.territory.exploration.domain.explorer.AccountIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 구글 OIDC 로그인 성공 → 계정 연결·병합(SessionLogin) → 화면(/#profile)으로. 로그인 전 기억한 익명 탐험가(login-intent)가 있으면 그
 * 탐험가를 연결·병합 대상으로 쓴다. 신원 값(sub·email)은 로그에 남기지 않는다.
 */
@Component
class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginSuccessHandler.class);
    static final String AFTER_LOGIN = "/#profile";

    private final SessionLogin sessionLogin;

    GoogleLoginSuccessHandler(SessionLogin sessionLogin) {
        this.sessionLogin = sessionLogin;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
        throws IOException {
        SessionAccount account = SessionAccount.of(authentication)
            .orElseThrow(() -> new IllegalStateException("OAuth2 인증 객체가 아닙니다: " + authentication.getClass()));
        LoginResponse result = sessionLogin.login(request, response,
            new AccountIdentity(account.provider(), account.subject(), account.email()), sessionLogin.pending(request));
        log.info("구글 로그인: {} (explorer {})", result.outcome(), result.explorerId());
        response.sendRedirect(AFTER_LOGIN);
    }
}
