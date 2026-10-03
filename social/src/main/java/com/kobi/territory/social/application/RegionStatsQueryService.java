package com.kobi.territory.social.application;

import com.kobi.territory.social.api.query.RegionStatsQuery;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link RegionStatsQuery} 구현 — 일 1회 집계 스냅숏(애플리케이션 캐시 포함)을 공개 DTO 로 옮긴다(8단계). */
@Service
public class RegionStatsQueryService implements RegionStatsQuery {

    private final RankingService rankings;

    public RegionStatsQueryService(RankingService rankings) {
        this.rankings = rankings;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegionVisitorsView> regionVisitors() {
        return rankings.regionStats().stream()
            .map(stat -> new RegionVisitorsView(stat.regionCode(), stat.visitorCount(), stat.population())).toList();
    }
}
