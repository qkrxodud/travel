package com.kobi.territory.analytics.api.web;

import com.kobi.territory.analytics.application.PublicViewRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 공개 페이지 열람 기록(10단계) — {@code GET /u/**} 의 응답이 200 이면 공개 프로필·자랑 카드·VS 카드 열람을 적는다(304·404 는 세지
 * 않는다). User-Agent 는 봇·기기 유형을 거칠게 가르는 데만 쓰고 handle·주소는 적지 않는다. 기록이 실패해도 응답에는 영향이 없다.
 */
@Component
public class PublicViewFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PublicViewFilter.class);

    private final PublicViewRecorder recorder;

    public PublicViewFilter(PublicViewRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod()) || !request.getRequestURI().startsWith(request.getContextPath() + "/u/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        chain.doFilter(request, response);
        if (response.getStatus() != HttpServletResponse.SC_OK) return;
        try {
            recorder.record(request.getRequestURI().substring(request.getContextPath().length()), request.getHeader(HttpHeaders.USER_AGENT),
                RequestOrigin.of(request));
        } catch (RuntimeException failure) {
            log.warn("분석: 공개 페이지 열람 기록 실패(응답에는 영향 없음): {}", failure.toString());
        }
    }
}
