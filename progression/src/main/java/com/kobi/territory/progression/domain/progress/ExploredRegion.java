package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 탐험가가 밟은 지역 한 곳(explorer_region 행). 여러 지도에서 같은 지역을 칠해도 하나다.
 * activeMaps: 지금 이 지역 방문이 살아 있는 지도들 — 개수가 active_map_count(D2). 0이 되면 기본 XP 회수 대상.
 * 지도 id 집합으로 들고 있는 이유: 같은 RegionVisited/VisitCancelled 가 두 번 와도 +1/−1 이 두 번 되지 않게(멱등).
 * 다음 상태(지도 추가·제거)를 계산하므로 class(class vs record 기준).
 */
public final class ExploredRegion {

    private final RegionCode code;
    private final String provinceCode;
    private final Rarity rarity;
    /** 처음 밟은 처리 시각(전체 랭킹·상위%·도감 뱃지용 — 탈퇴로 지도가 사라져도 남는다). */
    private final Instant firstVisitedAt;
    private final Set<String> activeMaps;

    private ExploredRegion(RegionCode code, String provinceCode, Rarity rarity, Instant firstVisitedAt,
                           Set<String> activeMaps) {
        this.code = Objects.requireNonNull(code, "code");
        this.provinceCode = Objects.requireNonNull(provinceCode, "provinceCode");
        this.rarity = Objects.requireNonNull(rarity, "rarity");
        this.firstVisitedAt = Objects.requireNonNull(firstVisitedAt, "firstVisitedAt");
        this.activeMaps = Set.copyOf(new TreeSet<>(activeMaps));
    }

    /** 저장된 값으로 복원. */
    public static ExploredRegion restore(RegionCode code, String provinceCode, Rarity rarity, Instant firstVisitedAt,
                                         Set<String> activeMaps) {
        return new ExploredRegion(code, provinceCode, rarity, firstVisitedAt, activeMaps);
    }

    /** 처음 밟은 지역(아직 활성 지도 없음). */
    static ExploredRegion firstVisit(RegionCode code, String provinceCode, Rarity rarity, Instant at) {
        return new ExploredRegion(code, provinceCode, rarity, at, Set.of());
    }

    public boolean active() {
        return !activeMaps.isEmpty();
    }

    public int activeMapCount() {
        return activeMaps.size();
    }

    ExploredRegion withMap(String mapId) {
        Set<String> maps = new TreeSet<>(activeMaps);
        maps.add(mapId);
        return new ExploredRegion(code, provinceCode, rarity, firstVisitedAt, maps);
    }

    ExploredRegion withoutMap(String mapId) {
        Set<String> maps = new TreeSet<>(activeMaps);
        maps.remove(mapId);
        return new ExploredRegion(code, provinceCode, rarity, firstVisitedAt, maps);
    }

    /** 재계산용: 처음 밟은 시각만 남기고 활성 지도를 비운 사본. */
    ExploredRegion deactivated() {
        return new ExploredRegion(code, provinceCode, rarity, firstVisitedAt, Set.of());
    }

    public RegionCode code() { return code; }
    public String provinceCode() { return provinceCode; }
    public Rarity rarity() { return rarity; }
    public Instant firstVisitedAt() { return firstVisitedAt; }
    public Set<String> activeMaps() { return activeMaps; }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExploredRegion region && code.equals(region.code) && provinceCode.equals(region.provinceCode)
            && rarity == region.rarity && firstVisitedAt.equals(region.firstVisitedAt) && activeMaps.equals(region.activeMaps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, provinceCode, rarity, firstVisitedAt, activeMaps);
    }

    @Override
    public String toString() {
        return "ExploredRegion[" + code + ", maps=" + activeMaps + ", first=" + firstVisitedAt + "]";
    }
}
