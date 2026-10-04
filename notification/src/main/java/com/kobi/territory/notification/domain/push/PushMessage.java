package com.kobi.territory.notification.domain.push;

import com.kobi.territory.notification.domain.policy.NotificationKind;
import java.util.Objects;

/**
 * 기기에 보일 알림 한 건. url 은 눌렀을 때 열 앱 안 경로(같은 출처, "/" 로 시작), tag 는 같은 알림을 기기에서 하나로 겹치는 이름.
 * 개인정보(지역 이름·handle·메모)는 싣지 않는다 — 미스터리 지역 이름도 감춘다.
 */
public record PushMessage(NotificationKind kind, String title, String body, String url, String tag) {

    public PushMessage {
        Objects.requireNonNull(kind, "kind");
        if (title == null || title.isBlank() || title.length() > 80) throw new IllegalArgumentException("알림 제목은 1~80자");
        if (body == null || body.isBlank() || body.length() > 240) throw new IllegalArgumentException("알림 본문은 1~240자");
        if (url == null || !url.startsWith("/") || url.startsWith("//") || url.length() > 200) {
            throw new IllegalArgumentException("알림 경로는 / 로 시작하는 앱 안 경로");
        }
        if (tag == null || !tag.matches("[a-z0-9-]{1,64}")) throw new IllegalArgumentException("알림 tag 형식");
    }
}
