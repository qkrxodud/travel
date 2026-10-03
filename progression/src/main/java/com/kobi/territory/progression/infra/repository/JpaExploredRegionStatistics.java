package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.domain.progress.ExploredRegionStatistics;
import com.kobi.territory.progression.domain.progress.ProvinceTally;
import com.kobi.territory.progression.domain.progress.RegionVisitorTally;
import com.kobi.territory.progression.infra.entity.ProvinceTallyRow;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Repository;

/** 탐험가 단위 지역 집계 어댑터(5단계) — explorer_region 을 GROUP BY 로 센다. 행 → 도메인 변환은 프로젝션 행이 한다. */
@Repository
class JpaExploredRegionStatistics implements ExploredRegionStatistics {

    private static final int CHUNK = 1000;

    private final ExplorerRegionJpaRepository regionRows;

    JpaExploredRegionStatistics(ExplorerRegionJpaRepository regionRows) {
        this.regionRows = regionRows;
    }

    @Override
    public List<ProvinceTally> provinceTallies(Collection<ExplorerId> explorerIds) {
        if (explorerIds.isEmpty()) return List.of();
        return regionRows.provinceTallies(explorerIds.stream().map(ExplorerId::value).toList()).stream()
            .map(ProvinceTallyRow::toDomain).toList();
    }

    @Override
    public List<ProvinceTally> allProvinceTallies() {
        return regionRows.allProvinceTallies().stream().map(ProvinceTallyRow::toDomain).toList();
    }

    /** IN 목록을 CHUNK 씩 나눠 묻고 지역별로 더한다(저장 기술상의 처리 — 탐험가는 서로 겹치지 않으므로 합이 곧 전체). */
    @Override
    public List<RegionVisitorTally> regionVisitors(Collection<ExplorerId> explorerIds) {
        List<String> ids = explorerIds.stream().map(ExplorerId::value).distinct().toList();
        Map<String, Long> byRegion = new TreeMap<>();
        for (int from = 0; from < ids.size(); from += CHUNK) {
            regionRows.regionVisitors(ids.subList(from, Math.min(from + CHUNK, ids.size())))
                .forEach(row -> byRegion.merge(row.getRegionCode(), row.getVisitorCount(), Long::sum));
        }
        return byRegion.entrySet().stream()
            .map(entry -> new RegionVisitorTally(RegionCode.of(entry.getKey()), Math.toIntExact(entry.getValue()))).toList();
    }

    @Override
    public List<RegionCode> activeRegionCodes(ExplorerId explorerId) {
        return regionRows.activeRegionCodes(explorerId.value()).stream().map(RegionCode::of).toList();
    }
}
