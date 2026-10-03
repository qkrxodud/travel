package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.exploration.domain.ExplorationError;
import java.util.Arrays;
import java.util.Locale;

/**
 * 지도 공개 범위(전체·친구·비공개). 기본 비공개. 4단계: PUBLIC 공유 지도는 지도장의 공개 프로필(/u/{handle})에 보이고 그 링크로
 * 합류할 수 있다(초대코드 없이). FRIENDS 는 5단계 친구 기능 전까지 PRIVATE 처럼 동작한다.
 */
public enum MapVisibility {
    PUBLIC, FRIENDS, PRIVATE;

    /** 공개 프로필에 보이고 프로필 링크로 합류할 수 있는지. */
    public boolean openToProfile() {
        return this == PUBLIC;
    }

    /** 요청 값 → 공개 범위. 생략하면 PRIVATE, 모르는 값이면 INVALID_SETTINGS. */
    public static MapVisibility parseOrPrivate(String raw) {
        if (raw == null || raw.isBlank()) return PRIVATE;
        String normalized = raw.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.name().equals(normalized)).findFirst()
            .orElseThrow(() -> ExplorationError.INVALID_SETTINGS.exception("visibility=" + raw));
    }
}
