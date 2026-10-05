package com.kobi.territory.catalog.domain.lineup;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 한 번 모은 회차 지역 목록(후보 또는 확정).
 *
 * @param collectedAt 근거 자료를 읽은 시각
 * @param warnings    모을 때 난 경고(부족분 채움 등)
 */
public record LineupSnapshot(LineupRegions regions, Instant collectedAt, List<String> warnings) {

    public LineupSnapshot {
        Objects.requireNonNull(regions, "regions");
        Objects.requireNonNull(collectedAt, "collectedAt");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
