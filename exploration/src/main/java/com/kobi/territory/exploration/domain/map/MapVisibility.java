package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.exploration.domain.ExplorationError;
import java.util.Arrays;
import java.util.Locale;

/** 지도 공개 범위(전체·친구·비공개). 기본 비공개. */
public enum MapVisibility {
    PUBLIC, FRIENDS, PRIVATE;

    /** 요청 값 → 공개 범위. 생략하면 PRIVATE, 모르는 값이면 INVALID_SETTINGS. */
    public static MapVisibility parseOrPrivate(String raw) {
        if (raw == null || raw.isBlank()) return PRIVATE;
        String normalized = raw.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.name().equals(normalized)).findFirst()
            .orElseThrow(() -> ExplorationError.INVALID_SETTINGS.exception("visibility=" + raw));
    }
}
