package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;

/**
 * 체크인 처리 시각이 속한 주의 미스터리 지역(8단계 — 카탈로그 기록을 application 이 옮긴 값). 그 주 기록이 없으면(지난 주를 나중에
 * 고르지 않는다 — 소급 없음) 체크인 사실에 싣지 않는다.
 *
 * @param weekId 그 주 월요일(ISO 날짜) — 장부 refId {@code mystery:{explorerId}:{weekId}}
 */
public record MysteryFact(String weekId, RegionCode region) {
    public MysteryFact {
        Objects.requireNonNull(weekId, "weekId");
        Objects.requireNonNull(region, "region");
    }

    /** 이 지역이 그 주의 미스터리 지역인지. */
    public boolean covers(RegionCode visited) {
        return region.equals(visited);
    }
}
