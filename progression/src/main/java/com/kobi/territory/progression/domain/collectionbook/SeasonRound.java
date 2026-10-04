package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 계절 한정 테마의 한 회차(예: autumn-2026). 기간은 [startsAt, endsAt) — endsAt 순간부터 닫힌다. 판정 메서드만 있는 값이라 record.
 *
 * @param roundId  {계절 id}-{연도}
 * @param seasonId 계절 id(칭호 season-{id} 는 회차와 무관하게 하나)
 */
public record SeasonRound(String roundId, String seasonId, int year, Instant startsAt, Instant endsAt, Set<RegionCode> regions) {

    public SeasonRound {
        Objects.requireNonNull(roundId, "roundId");
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        regions = Set.copyOf(regions);
    }

    /** 이 시각이 회차 기간 안인지(처리 시각 기준). */
    public boolean openAt(Instant at) {
        return !at.isBefore(startsAt) && at.isBefore(endsAt);
    }

    /** 이 시각에 이미 닫혔는지. */
    public boolean endedBy(Instant at) {
        return !at.isBefore(endsAt);
    }

    public boolean includes(RegionCode region) {
        return regions.contains(region);
    }

    /** 모은 지역이 이 회차의 지역을 모두 덮는지. */
    public boolean completedBy(Set<RegionCode> collected) {
        return collected.containsAll(regions);
    }

    /** 회차 id 의 계절 id(마지막 '-' 앞). */
    public static String seasonOf(String roundId) {
        return roundId.substring(0, roundId.lastIndexOf('-'));
    }
}
