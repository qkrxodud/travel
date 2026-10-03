package com.kobi.territory.progression.application;

import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.ProgressionRules.TitleView;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.api.query.AchievementsView;
import com.kobi.territory.progression.api.query.ProgressQuery;
import com.kobi.territory.progression.api.query.ProgressSummaryView;
import com.kobi.territory.progression.domain.progress.ExplorerProgress;
import com.kobi.territory.progression.domain.progress.ExplorerProgressRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ProgressQuery} 구현 — 진행 조회(ProgressService)를 공개 DTO 로 옮긴다(칭호 이름은 카탈로그 정의). */
@Service
public class ProgressQueryService implements ProgressQuery {

    private final ProgressService progresses;
    private final ProgressionCatalog catalog;
    private final ProgressionRules rules;
    private final ExplorerProgressRepository progressRepository;

    public ProgressQueryService(ProgressService progresses, ProgressionCatalog catalog, ProgressionRules rules,
                                ExplorerProgressRepository progressRepository) {
        this.progressRepository = progressRepository;
        this.progresses = progresses;
        this.catalog = catalog;
        this.rules = rules;
    }

    @Override
    @Transactional(readOnly = true)
    public ProgressSummaryView summaryOf(String explorerId) {
        ExplorerProgress progress = progresses.view(ExplorerId.of(explorerId));
        String titleId = progress.displayTitle(catalog.policy());
        String titleName = rules.titles().stream().filter(title -> title.id().equals(titleId)).map(TitleView::name)
            .findFirst().orElse(null);
        return new ProgressSummaryView(progress.xp(), progress.level(), titleName,
            progress.streakMonthsAsOf(catalog.currentMonth()), progress.badges().size());
    }

    @Override
    @Transactional(readOnly = true)
    public AchievementsView achievementsOf(String explorerId) {
        return progressRepository.find(ExplorerId.of(explorerId)).map(progress -> new AchievementsView(
                progress.milestonesReached().entrySet().stream()
                    .map(reached -> new AchievementsView.Milestone(reached.getKey(), reached.getValue())).toList(),
                progress.provincesConquered().entrySet().stream()
                    .map(conquered -> new AchievementsView.Conquest(conquered.getKey(), conquered.getValue())).toList()))
            .orElseGet(() -> new AchievementsView(List.of(), List.of()));
    }
}
