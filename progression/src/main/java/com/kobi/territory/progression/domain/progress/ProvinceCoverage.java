package com.kobi.territory.progression.domain.progress;

import java.time.Instant;
import java.util.Objects;

/**
 * 탐험가 단위 시·도 칠하기 현황 한 줄(8단계 — 시·도별 정복률 목록). 현행 지역 기준(폐지 지역 제외).
 *
 * @param complete    지금 현행 지역을 모두 칠한 상태인지
 * @param conqueredAt 정복 보상을 받은 시각(한 번 정복하면 취소해도 남는다), 아직이면 null
 */
public record ProvinceCoverage(String provinceCode, int covered, int total, boolean complete, Instant conqueredAt) {
    public ProvinceCoverage {
        Objects.requireNonNull(provinceCode, "provinceCode");
    }

    /** 정복 기록이 있는지(왕관 — 회수 없음). */
    public boolean conquered() {
        return conqueredAt != null;
    }

    /** 칠한 비율(%, 내림). 지역이 없으면 0. */
    public int percent() {
        return total == 0 ? 0 : covered * 100 / total;
    }
}
