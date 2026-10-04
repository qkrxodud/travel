package com.kobi.territory.notification.domain.push;

import com.kobi.territory.notification.domain.NotificationError;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 브라우저 구독 주소(푸시 서비스가 정해 준 URL). 한 브라우저 구독 = 한 주소라 기기의 열쇠가 된다. http(s) 절대 주소, 1,024자 이하,
 * 사용자 정보·조각(#) 없음. 어느 호스트를 받을지는 {@code EndpointRules}(recipient)가 정한다. 주소 원문이 길어 저장소의 유일 열쇠로는
 * {@link #fingerprint()}(SHA-256)를 쓴다.
 */
public record PushEndpoint(String value) {

    public static final int MAX_LENGTH = 1024;

    public PushEndpoint {
        if (value == null || value.isBlank()) throw NotificationError.INVALID_PUSH_SUBSCRIPTION.exception("주소 없음");
        value = value.trim();
        if (value.length() > MAX_LENGTH) throw NotificationError.INVALID_PUSH_SUBSCRIPTION.exception("주소가 너무 김");
        URI uri = parse(value);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!uri.isAbsolute() || !(scheme.equals("https") || scheme.equals("http")) || uri.getHost() == null
            || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw NotificationError.INVALID_PUSH_SUBSCRIPTION.exception("주소 형식");
        }
    }

    public static PushEndpoint of(String value) {
        return new PushEndpoint(value);
    }

    public URI uri() {
        return parse(value);
    }

    public String host() {
        return uri().getHost().toLowerCase(Locale.ROOT);
    }

    public boolean secure() {
        return "https".equalsIgnoreCase(uri().getScheme());
    }

    /** 저장소 열쇠 — 주소의 SHA-256(소문자 16진 64자). */
    public String fingerprint() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", unavailable);
        }
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException malformed) {
            throw NotificationError.INVALID_PUSH_SUBSCRIPTION.exception("주소 형식");
        }
    }

    /** 로그용 — 주소 끝(구독 고유 값)은 감춘다. */
    @Override
    public String toString() {
        URI uri = uri();
        return uri.getScheme() + "://" + uri.getRawAuthority() + "/…";
    }
}
