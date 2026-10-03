package com.kobi.territory.progression.application;

import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.ProgressionRules.TitleView;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.api.query.ProgressQuery;
import com.kobi.territory.progression.api.query.ProgressSummaryView;
import com.kobi.territory.progression.domain.progress.ExplorerProgress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ProgressQuery} 구현 — 진행 조회(ProgressService)를 공개 DTO 로 옮긴다(칭호 이름은 카탈로그 정의). */
@Service
public class ProgressQueryService implements ProgressQuery {

    private final ProgressService progresses;
    private final ProgressionCatalog catalog;
    private final ProgressionRules rules;

    public ProgressQueryService(ProgressService progresses, ProgressionCatalog catalog, ProgressionRules rules) {
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
            progress.streak().asOf(catalog.currentMonth()), progress.badges().size());
    }
}
