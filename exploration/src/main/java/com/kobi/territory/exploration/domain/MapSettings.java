package com.kobi.territory.exploration.domain;

import java.util.Objects;

/**
 * 지도 설정. dailyCheckInCap 이 하루 상한의 진실 원천이며, 생성 시 기본값은
 * territory.check-in.daily-cap 설정에서 온다(application이 주입).
 */
public record MapSettings(boolean photoRequired, int dailyCheckInCap, MapVisibility visibility) {
    public MapSettings {
        if (dailyCheckInCap < 1) throw ExplorationError.INVALID_MAP.exception("dailyCheckInCap=" + dailyCheckInCap);
        Objects.requireNonNull(visibility, "visibility");
    }

    public static MapSettings defaults(int defaultDailyCap) {
        return new MapSettings(false, defaultDailyCap, MapVisibility.PRIVATE);
    }
}
