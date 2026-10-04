package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.domain.progress.StreakStanding;
import com.kobi.territory.progression.domain.progress.StreakStandings;
import com.kobi.territory.progression.infra.entity.FreezeHeldRow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** 연속 탐험 상태 어댑터(12단계) — explorer_progress 의 스트릭 두 칸 + streak_freeze 합계. IN 목록은 CHUNK 씩 나눠 묻는다. */
@Repository
class JpaStreakStandings implements StreakStandings {

    private static final int CHUNK = 1000;

    private final ExplorerProgressJpaRepository progressRows;
    private final StreakFreezeJpaRepository freezeRows;

    JpaStreakStandings(ExplorerProgressJpaRepository progressRows, StreakFreezeJpaRepository freezeRows) {
        this.progressRows = progressRows;
        this.freezeRows = freezeRows;
    }

    @Override
    public List<StreakStanding> of(Collection<ExplorerId> explorerIds) {
        List<String> ids = explorerIds.stream().map(ExplorerId::value).distinct().toList();
        List<StreakStanding> standings = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += CHUNK) {
            List<String> chunk = ids.subList(from, Math.min(from + CHUNK, ids.size()));
            Map<String, Long> held = freezeRows.heldBy(chunk).stream()
                .collect(Collectors.toMap(FreezeHeldRow::getExplorerId, FreezeHeldRow::getHeld));
            progressRows.findByExplorerIdIn(chunk).forEach(row ->
                standings.add(row.toStreakStanding(Math.toIntExact(held.getOrDefault(row.explorerId(), 0L)))));
        }
        return standings;
    }
}
