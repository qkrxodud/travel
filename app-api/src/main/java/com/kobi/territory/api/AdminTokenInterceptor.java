package com.kobi.territory.api;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /admin/** 보호(리더 결정 4): 요청 헤더 {@code X-Admin-Token} 이 설정값 territory.admin.token 과 같아야 한다.
 * 없으면 401 ADMIN_TOKEN_REQUIRED, 다르면 403 ADMIN_TOKEN_INVALID. 비교는 상수 시간(MessageDigest.isEqual).
 * 4단계 로그인 도입 후 역할 기반 인가로 대체한다.
 */
class AdminTokenInterceptor implements HandlerInterceptor {

    static final String HEADER = "X-Admin-Token";

    private final byte[] expected;

    AdminTokenInterceptor(String token) {
        if (token == null || token.isBlank()) throw new IllegalStateException("territory.admin.token 이 비어 있습니다.");
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String given = request.getHeader(HEADER);
        if (given == null || given.isBlank()) {
            throw new TerritoryException("ADMIN_TOKEN_REQUIRED", ErrorKind.UNAUTHENTICATED,
                HEADER + " 헤더로 관리자 토큰을 보내 주세요.");
        }
        if (!MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8))) {
            throw new TerritoryException("ADMIN_TOKEN_INVALID", ErrorKind.FORBIDDEN, "관리자 토큰이 올바르지 않습니다.");
        }
        return true;
    }
}
