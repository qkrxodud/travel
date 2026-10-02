package com.kobi.territory.progression.domain;

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
 *
 * @param firstVisitedAt 처음 밟은 처리 시각(전체 랭킹·상위%·도감 뱃지용 — 탈퇴로 지도가 사라져도 남는다)
 */
public record ExploredRegion(RegionCode code, String provinceCode, Rarity rarity, Instant firstVisitedAt,
                             Set<String> activeMaps) {

    public ExploredRegion {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(provinceCode, "provinceCode");
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(firstVisitedAt, "firstVisitedAt");
        activeMaps = Set.copyOf(new TreeSet<>(activeMaps));
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
}
