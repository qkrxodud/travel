package com.kobi.territory.config;

import com.kobi.territory.catalog.application.RegionVisitorCounts;
import com.kobi.territory.social.api.query.RegionStatsQuery;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 조립(8단계): 카탈로그의 지역별 방문자 수 포트 ← 소셜의 일 1회 집계 Query. 이번 주 미스터리 지역의 "덜 알려진 곳" 판단에 쓴다.
 * 카탈로그는 다른 컨텍스트를 참조하지 않으므로 app-api 만 잇는다. 도메인 로직 없음(전달만).
 */
@Component
class RegionVisitorCountsAdapter implements RegionVisitorCounts {

    private final RegionStatsQuery regionStats;

    RegionVisitorCountsAdapter(RegionStatsQuery regionStats) {
        this.regionStats = regionStats;
    }

    @Override
    public List<Count> counts() {
        return regionStats.regionVisitors().stream()
            .map(visitors -> new Count(visitors.regionCode(), visitors.visitorCount(), visitors.population())).toList();
    }
}
