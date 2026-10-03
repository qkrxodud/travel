package com.kobi.territory.progression.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.api.query.ExplorerRegionQuery;
import com.kobi.territory.progression.api.query.ProvinceTallyView;
import com.kobi.territory.progression.api.query.RegionVisitorsView;
import com.kobi.territory.progression.domain.progress.ExploredRegionStatistics;
import com.kobi.territory.progression.domain.progress.ProvinceTally;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ExplorerRegionQuery} 구현 — 탐험가 단위 지역 집계 포트를 공개 DTO 로 옮긴다(5단계). */
@Service
@Transactional(readOnly = true)
public class ExplorerRegionQueryService implements ExplorerRegionQuery {

    private final ExploredRegionStatistics statistics;

    public ExplorerRegionQueryService(ExploredRegionStatistics statistics) {
        this.statistics = statistics;
    }

    @Override
    public List<ProvinceTallyView> provinceTalliesOf(Collection<String> explorerIds) {
        return views(statistics.provinceTallies(explorerIds.stream().map(ExplorerId::of).toList()));
    }

    @Override
    public List<ProvinceTallyView> provinceTallies() {
        return views(statistics.allProvinceTallies());
    }

    @Override
    public List<RegionVisitorsView> regionVisitorsAmong(Collection<String> explorerIds) {
        return statistics.regionVisitors(explorerIds.stream().map(ExplorerId::of).toList()).stream()
            .map(tally -> new RegionVisitorsView(tally.regionCode().value(), tally.visitorCount())).toList();
    }

    @Override
    public List<String> activeRegionCodesOf(String explorerId) {
        return statistics.activeRegionCodes(ExplorerId.of(explorerId)).stream().map(RegionCode::value).toList();
    }

    private static List<ProvinceTallyView> views(List<ProvinceTally> tallies) {
        return tallies.stream()
            .map(tally -> new ProvinceTallyView(tally.explorerId().value(), tally.provinceCode(), tally.regionCount())).toList();
    }
}
