package com.kobi.territory.analytics.api.web;

import com.kobi.territory.analytics.domain.ratelimit.ClientOrigin;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청 → {@link ClientOrigin}. 실제 접속 주소와 프록시 헤더 원문은 <b>가장 안쪽 요청</b>(서블릿 컨테이너의 것)에서 읽는다 — 운영의
 * {@code server.forward-headers-strategy: framework} 가 요청을 감싸 X-Forwarded-For 맨 왼쪽(클라이언트가 정할 수 있는 값)을
 * {@code getRemoteAddr()} 로 내주고 원래 헤더를 숨기기 때문(10단계 QA P2-2). 어느 값을 믿을지는 {@code TrustedProxies} 가 정한다.
 */
final class RequestOrigin {

    static final String CLOUDFLARE_IP = "CF-Connecting-IP";
    static final String FORWARDED_FOR = "X-Forwarded-For";
    static final String COUNTRY = "CF-IPCountry";

    private RequestOrigin() {}

    static ClientOrigin of(HttpServletRequest request) {
        HttpServletRequest raw = innermost(request);
        return new ClientOrigin(raw.getRemoteAddr(), raw.getHeader(CLOUDFLARE_IP), raw.getHeader(FORWARDED_FOR), raw.getHeader(COUNTRY));
    }

    private static HttpServletRequest innermost(HttpServletRequest request) {
        ServletRequest current = request;
        while (current instanceof ServletRequestWrapper wrapper && wrapper.getRequest() instanceof HttpServletRequest inner) {
            current = inner;
        }
        return (HttpServletRequest) current;
    }
}
