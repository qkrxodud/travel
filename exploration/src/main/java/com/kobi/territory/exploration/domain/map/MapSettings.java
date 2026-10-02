package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.exploration.domain.ExplorationError;

/**
 * 지도 설정. dailyCheckInCap 이 하루 상한의 진실 원천이며, 생성 시 기본값은
 * territory.check-in.daily-cap 설정에서 온다(application이 주입). 지도장은 그 값 이하로만 낮출 수 있다(ExpeditionMap.changeSettings).
 */
public record MapSettings(boolean photoRequired, int dailyCheckInCap, MapVisibility visibility) {

    public MapSettings {
        if (dailyCheckInCap < 1) throw ExplorationError.INVALID_SETTINGS.exception("dailyCheckInCap=" + dailyCheckInCap);
        if (visibility == null) throw ExplorationError.INVALID_SETTINGS.exception("visibility 필요");
    }

    public static MapSettings defaults(int defaultDailyCap) {
        return new MapSettings(false, defaultDailyCap, MapVisibility.PRIVATE);
    }
}
