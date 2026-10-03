package com.kobi.territory.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.api.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 보안 설정(4단계, 파트 A 소유).
 * <ul>
 *   <li><b>인가</b>: 전부 permitAll — 누가 요청했는지는 기존처럼 {@code @CurrentExplorer} 리졸버(세션 → 토큰, {@link ExplorerAuthentication})가
 *       판단한다. 공개 경로(GET /u/** 공개 프로필·카드, /catalog/**, 정적 파일)는 리졸버를 쓰지 않으니 그대로 공개.</li>
 *   <li><b>로그인</b>: 구글 OIDC(oauth2Login) — 클라이언트 등록이 있을 때만(GoogleLoginConfig). 성공하면 계정 연결·병합 후 세션.
 *       local 은 /dev/login 이 같은 경로(SessionLogin)를 탄다. 로그아웃 POST /logout → 204.</li>
 *   <li><b>세션</b>: 같은 출처 웹이라 세션 쿠키(JSESSIONID, HttpOnly, SameSite=Lax, prod Secure). 세션에는 계정 신원만.</li>
 *   <li><b>CSRF</b>: 쿠키 토큰(XSRF-TOKEN → 헤더 X-XSRF-TOKEN). 검사 대상은 <b>세션이 있는</b> 변경 메서드 전부(토큰 헤더 유무와 무관 —
 *       QA P3-1). 세션 없는 요청(익명 발급 POST /explorers, 토큰 헤더 인증)은 위조할 세션이 없어 검사하지 않는다. local /dev/** 는 제외.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    static final String CSRF_INVALID = "CSRF_INVALID";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<ClientRegistrationRepository> registrations,
                                            GoogleLoginSuccessHandler loginSuccess, ObjectMapper objectMapper,
                                            PublicBaseUrl publicBaseUrl) throws Exception {
        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // XSRF-TOKEN 쿠키: SameSite=Lax, 공개 주소가 https 면 Secure(QA P3-12 — 프록시 뒤에서 request.isSecure() 가 거짓이어도)
        csrfTokens.setCookieCustomizer(cookie -> {
            cookie.sameSite("Lax");
            if (publicBaseUrl.secure()) cookie.secure(true);
        });
        http
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokens)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .requireCsrfProtectionMatcher(sessionMutations()))
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
            // 이전 단계 동작 유지: 캐시 헤더는 각 응답이 정한다(공유 카드 PNG 캐시 등), H2 콘솔(local)은 같은 출처 frame
            .headers(headers -> headers.cacheControl(cache -> cache.disable()).frameOptions(frame -> frame.sameOrigin()))
            .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, denied) ->
                writeError(response, objectMapper, HttpStatus.FORBIDDEN, CSRF_INVALID,
                    "보안 토큰이 맞지 않아요. 새로고침 후 다시 시도해 주세요.")))
            .logout(logout -> logout.logoutUrl("/logout")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
                .deleteCookies("JSESSIONID"));
        if (registrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth.successHandler(loginSuccess)
                .failureHandler((request, response, failure) -> response.sendRedirect("/#profile?login=failed")));
            log.info("구글 로그인 활성화(/oauth2/authorization/{})", GoogleLoginConfig.REGISTRATION_ID);
        } else {
            log.warn("구글 로그인 비활성 — GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET 환경변수가 없습니다(익명 탐험은 그대로 동작).");
        }
        return http.build();
    }

    /**
     * CSRF 검사 대상: 세션 있음 + 변경 메서드 + /dev/** 아님(QA P3-1). 세션이 있으면 X-Explorer-Token·X-Admin-Token 헤더가 있어도 검사한다 —
     * 인증은 세션이 먼저라, 헤더 유무로 면제하면 "세션 + 아무 토큰 헤더"가 CSRF 없이 세션으로 처리되는 우회로가 된다(CORS 를 열거나
     * 앱 클라이언트를 붙이는 순간 악용 가능). 토큰 인증 경로는 세션이 없을 때만 CSRF 대상이 아니다(위조할 세션이 없다).
     */
    static RequestMatcher sessionMutations() {
        return request -> !SAFE_METHODS.contains(request.getMethod())
            && request.getSession(false) != null
            && !request.getRequestURI().startsWith(request.getContextPath() + "/dev/");
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String code,
                                   String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(code, message));
    }

    /** 지연 생성되는 CSRF 토큰을 매 요청 꺼내 XSRF-TOKEN 쿠키가 늘 내려가게 한다(SPA 가 첫 변경 요청 전에 읽을 수 있게). */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken();
            chain.doFilter(request, response);
        }
    }
}
