package com.kobi.territory.sharing.domain.privacy;

import com.kobi.territory.sharing.domain.SharingError;
import java.util.Arrays;
import java.util.Locale;

/**
 * 공개 프로필 공개 범위(§7 — 전체·친구·비공개). FRIENDS 는 5단계 친구(Friendship) 기능 전까지 PRIVATE 처럼 동작한다
 * (프로필·카드 404). 친구 관계가 생기면 {@link #visibleToPublic()} 옆에 "친구에게 보이는지" 판단을 더한다.
 */
public enum ProfileVisibility {
    PUBLIC, FRIENDS, PRIVATE;

    /** 로그인하지 않은 사람(누구나)에게 보이는지. */
    public boolean visibleToPublic() {
        return this == PUBLIC;
    }

    /** 요청 값 → 공개 범위. 모르는 값·빈 값이면 INVALID_VISIBILITY. */
    public static ProfileVisibility parse(String raw) {
        String normalized = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.name().equals(normalized)).findFirst()
            .orElseThrow(() -> SharingError.INVALID_VISIBILITY.exception(String.valueOf(raw)));
    }
}
