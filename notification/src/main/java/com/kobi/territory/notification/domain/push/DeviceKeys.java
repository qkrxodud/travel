package com.kobi.territory.notification.domain.push;

import com.kobi.territory.notification.domain.NotificationError;
import java.util.Base64;

/**
 * 브라우저 구독의 암호화 키(base64url) — p256dh = 브라우저 공개 키(P-256 비압축 점 65바이트), auth = 인증 비밀 16바이트.
 * 서버는 이 키로 내용을 암호화해 푸시 서비스가 읽지 못하게 한다. 곡선 위의 점인지는 보낼 때 암호화가 다시 확인한다.
 */
public record DeviceKeys(String p256dh, String auth) {

    public DeviceKeys {
        p256dh = require(p256dh, 65, "p256dh");
        auth = require(auth, 16, "auth");
        if (Base64.getUrlDecoder().decode(p256dh)[0] != 0x04) throw NotificationError.INVALID_PUSH_SUBSCRIPTION.exception("p256dh");
    }

    public byte[] publicKeyBytes() {
        return Base64.getUrlDecoder().decode(p256dh);
    }

    public byte[] authBytes() {
        return Base64.getUrlDecoder().decode(auth);
    }

    /** 표준 base64 로 와도 받되 base64url(채움 없음)로 맞춘다. 길이가 다르면 거절. */
    private static String require(String value, int bytes, String name) {
        if (value == null || value.isBlank() || value.length() > 128) throw NotificationError.INVALID_PUSH_SUBSCRIPTION.exception(name);
        String normalized = value.trim().replace('+', '-').replace('/', '_').replace("=", "");
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(normalized);
            if (decoded.length != bytes) throw NotificationError.INVALID_PUSH_SUBSCRIPTION.exception(name);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);
        } catch (IllegalArgumentException malformed) {
            throw NotificationError.INVALID_PUSH_SUBSCRIPTION.exception(name);
        }
    }

    @Override
    public String toString() {
        return "DeviceKeys[****]";
    }
}
