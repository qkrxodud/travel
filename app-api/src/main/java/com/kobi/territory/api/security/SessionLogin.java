package com.kobi.territory.api.security;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.application.AccountService;
import com.kobi.territory.exploration.domain.explorer.AccountIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 로그인 → 세션. 구글 OIDC 성공 처리와 local /dev/login 이 같은 경로를 탄다(계정 연결·병합은 {@link AccountService#login}).
 * 세션 고정 방지: 이미 세션이 있으면 id 를 바꾼다. 세션에는 계정 신원({@link AccountAuthenticationToken})과 병합 안내만 둔다.
 */
@Component
public class SessionLogin {

    /** 구글 로그인으로 떠나기 전 기억한 "지금 기기의 익명 탐험가"(POST /auth/login-intent). */
    static final String PENDING_EXPLORER = "territory.login.pendingExplorer";
    /** 병합·연결 결과 안내(한 번 보여 주면 지운다 — GET /auth/session). */
    static final String NOTICE = "territory.login.notice";

    private final AccountService accounts;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public SessionLogin(AccountService accounts) {
        this.accounts = accounts;
    }

    public LoginResponse login(HttpServletRequest request, HttpServletResponse response, AccountIdentity identity,
                               ExplorerId currentExplorer) {
        LoginResponse result = LoginResponse.of(accounts.login(identity, currentExplorer), identity.email());
        if (request.getSession(false) != null) request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new AccountAuthenticationToken(identity.provider(), identity.subject(), identity.email()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        HttpSession session = request.getSession(true);
        session.removeAttribute(PENDING_EXPLORER);
        session.setAttribute(NOTICE, result);
        return result;
    }

    /** 구글 로그인 직전 — 지금 기기의 익명 탐험가를 세션에 기억한다(리다이렉트에는 헤더가 실리지 않으므로). */
    public void rememberPending(HttpServletRequest request, ExplorerId currentExplorer) {
        request.getSession(true).setAttribute(PENDING_EXPLORER, currentExplorer == null ? null : currentExplorer.value());
    }

    ExplorerId pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(PENDING_EXPLORER);
        return value == null ? null : ExplorerId.of(value.toString());
    }

    /** 병합 안내를 꺼낸다(한 번만). */
    LoginResponse takeNotice(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object notice = session.getAttribute(NOTICE);
        session.removeAttribute(NOTICE);
        return notice instanceof LoginResponse login ? login : null;
    }
}
