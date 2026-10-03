package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 진행이 보는 체크인 사실(RegionVisited 에서 application 이 옮긴 값).
 *
 * @param firstClaim 지도 안 선점 여부(이벤트 값 그대로) — 선점 보너스는 지도마다·수령자마다
 * @param generation 같은 (지도, 지역, 멤버)의 체크인 회차(결정 6). 0 = 예전 이벤트(회차 판단 안 함)
 * @param mystery    처리 시각이 속한 주의 미스터리 지역(8단계). 그 주 기록이 없으면 null
 */
public record ProgressVisit(String mapId, RegionCode region, String provinceCode, Rarity rarity, Instant visitedAt,
                            boolean firstClaim, int generation, MysteryFact mystery) {

    /** 회차를 모르는(예전) 체크인. */
    public ProgressVisit(String mapId, RegionCode region, String provinceCode, Rarity rarity, Instant visitedAt,
                         boolean firstClaim) {
        this(mapId, region, provinceCode, rarity, visitedAt, firstClaim, 0, null);
    }

    /** 미스터리 지역을 모르는 체크인(8단계 이전 경로). */
    public ProgressVisit(String mapId, RegionCode region, String provinceCode, Rarity rarity, Instant visitedAt,
                         boolean firstClaim, int generation) {
        this(mapId, region, provinceCode, rarity, visitedAt, firstClaim, generation, null);
    }

    public ProgressVisit {
        Objects.requireNonNull(mapId, "mapId");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(provinceCode, "provinceCode");
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(visitedAt, "visitedAt");
    }

    /** 이 체크인이 그 주의 미스터리 지역을 칠한 것이면 그 주 사실. */
    public Optional<MysteryFact> mysteryFound() {
        return Optional.ofNullable(mystery).filter(fact -> fact.covers(region));
    }
}
