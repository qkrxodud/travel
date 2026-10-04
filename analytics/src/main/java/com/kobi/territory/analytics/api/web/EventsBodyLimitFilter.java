package com.kobi.territory.analytics.api.web;

import com.kobi.territory.analytics.application.AnalyticsSettings;
import com.kobi.territory.analytics.domain.AnalyticsError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code POST /events} 본문 크기 상한(territory.analytics.ingest.max-body-bytes). 길이를 밝힌 요청은 바로 보고, 길이를 모르는(chunked)
 * 요청은 상한 + 1 바이트까지만 읽어 본다 — 큰 본문을 메모리에 다 올리기 전에 413 EVENT_BATCH_TOO_LARGE 로 끊는다.
 */
@Component
public class EventsBodyLimitFilter extends OncePerRequestFilter {

    private final int maxBytes;

    public EventsBodyLimitFilter(AnalyticsSettings settings) {
        this.maxBytes = settings.maxBodyBytes();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !(request.getContextPath() + "/events").equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > maxBytes) {
            reject(response);
            return;
        }
        if (declared >= 0) {
            chain.doFilter(request, response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            reject(response);
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        var error = AnalyticsError.EVENT_BATCH_TOO_LARGE.exception(maxBytes + "바이트");
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"" + error.code() + "\",\"message\":\"" + error.getMessage() + "\"}");
    }

    /** 미리 읽은 본문을 다시 내주는 요청. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return source.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                @Override public int read() { return source.read(); }
                @Override public int read(byte[] buffer, int offset, int length) { return source.read(buffer, offset, length); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
