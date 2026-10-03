package com.kobi.territory.api.security;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.application.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 상태(4단계 화면 — 프로필 탭).
 * <ul>
 *   <li>{@code GET /auth/session} → {googleLoginEnabled, loginUrl, loggedIn, explorerId, handle, email, personalMapId, mergeNotice}.
 *       mergeNotice(병합·연결 안내)는 한 번 보여 주면 지운다.</li>
 *   <li>{@code POST /auth/login-intent}(X-Explorer-Token 선택) → 지금 기기의 익명 탐험가를 세션에 기억하고 {loginUrl} 을 준다 —
 *       구글로 리다이렉트하면 헤더가 실리지 않으므로. 구글 로그인이 비활성이면 googleLoginEnabled=false, loginUrl=null.</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    static final String LOGIN_URL = "/oauth2/authorization/" + GoogleLoginConfig.REGISTRATION_ID;

    private final ObjectProvider<ClientRegistrationRepository> registrations;
    private final ExplorerAuthentication authentication;
    private final AccountService accounts;
    private final SessionLogin sessionLogin;

    public AuthController(ObjectProvider<ClientRegistrationRepository> registrations, ExplorerAuthentication authentication,
                          AccountService accounts, SessionLogin sessionLogin) {
        this.registrations = registrations;
        this.authentication = authentication;
        this.accounts = accounts;
        this.sessionLogin = sessionLogin;
    }

    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest request) {
        Optional<SessionAccount> session = SessionAccount.of(SecurityContextHolder.getContext().getAuthentication());
        Optional<AccountService.AccountView> account = session
            .flatMap(identity -> accounts.explorerIdByAccount(identity.provider(), identity.subject()))
            .flatMap(explorerId -> accounts.view(ExplorerId.of(explorerId)));
        Map<String, Object> body = loginLinks();
        body.put("loggedIn", account.isPresent());
        body.put("explorerId", account.map(view -> view.explorer().id().value()).orElse(null));
        body.put("handle", account.map(view -> view.explorer().handle().value()).orElse(null));
        body.put("email", account.flatMap(view -> session.map(SessionAccount::email)).orElse(null));
        body.put("personalMapId", account.map(view -> view.personalMapId().value()).orElse(null));
        body.put("mergeNotice", account.map(view -> sessionLogin.takeNotice(request)).orElse(null));
        return body;
    }

    @PostMapping("/login-intent")
    public Map<String, Object> loginIntent(HttpServletRequest request) {
        Map<String, Object> body = loginLinks();
        if (Boolean.TRUE.equals(body.get("googleLoginEnabled"))) {
            sessionLogin.rememberPending(request, authentication.anonymous(request).orElse(null));
        }
        return body;
    }

    private Map<String, Object> loginLinks() {
        boolean enabled = registrations.getIfAvailable() != null;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("googleLoginEnabled", enabled);
        body.put("loginUrl", enabled ? LOGIN_URL : null);
        return body;
    }
}
