package com.kobi.territory.progression.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.api.query.StreakQuery;
import com.kobi.territory.progression.api.query.StreakStandingView;
import com.kobi.territory.progression.domain.progress.StreakStanding;
import com.kobi.territory.progression.domain.progress.StreakStandings;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link StreakQuery} 구현 — 연속 탐험 상태를 읽어 이번 달 기준 공개 DTO 로 옮긴다(판단은 Streak). */
@Service
public class StreakQueryService implements StreakQuery {

    private final StreakStandings standings;
    private final ProgressionCatalog catalog;

    public StreakQueryService(StreakStandings standings, ProgressionCatalog catalog) {
        this.standings = standings;
        this.catalog = catalog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StreakStandingView> standingsOf(Collection<String> explorerIds) {
        YearMonth month = catalog.currentMonth();
        return standings.of(explorerIds.stream().map(ExplorerId::of).toList()).stream().map(standing -> view(standing, month)).toList();
    }

    private static StreakStandingView view(StreakStanding standing, YearMonth month) {
        return new StreakStandingView(standing.explorerId().value(), month.toString(), standing.monthsAsOf(month),
            standing.checkedInIn(month), standing.freezesHeld(), standing.freezesNeededIfMissed(month), standing.atRiskIn(month));
    }
}
