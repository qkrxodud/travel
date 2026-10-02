package com.kobi.territory.progression.api.web;

import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.ProgressionRules.TitleView;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.api.web.ProgressionDtos.BadgeResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.ProgressResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.SelectTitleRequest;
import com.kobi.territory.progression.api.web.ProgressionDtos.StreakResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.TitleRef;
import com.kobi.territory.progression.api.web.ProgressionDtos.TitleResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.XpEntryResponse;
import com.kobi.territory.progression.application.ProgressService;
import com.kobi.territory.progression.application.ProgressionCatalog;
import com.kobi.territory.progression.domain.ExplorerProgress;
import com.kobi.territory.progression.domain.LevelCurve;
import com.kobi.territory.progression.domain.Streak;
import java.time.YearMonth;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 진행 조회·칭호 선택. 값은 이벤트로 비동기 반영된다(최종 일관성). */
@RestController
public class ProgressController {

    private final ProgressService progressService;
    private final ProgressionCatalog policy;
    private final ProgressionRules rules;

    public ProgressController(ProgressService progressService, ProgressionCatalog policy, ProgressionRules rules) {
        this.progressService = progressService;
        this.policy = policy;
        this.rules = rules;
    }

    @GetMapping("/progress")
    public ProgressResponse progress(@CurrentExplorer ExplorerId explorerId) {
        return toResponse(progressService.view(explorerId));
    }

    @PutMapping("/progress/title")
    public ProgressResponse selectTitle(@CurrentExplorer ExplorerId explorerId, @RequestBody SelectTitleRequest request) {
        return toResponse(progressService.selectTitle(explorerId, request.titleId()));
    }

    private ProgressResponse toResponse(ExplorerProgress progress) {
        LevelCurve curve = policy.policy().curve();
        YearMonth now = policy.currentMonth();
        Map<String, TitleView> titlesById = rules.titles().stream()
            .collect(Collectors.toMap(TitleView::id, Function.identity()));
        String displayTitle = progress.displayTitle(policy.policy());
        Streak streak = progress.streak();
        return new ProgressResponse(progress.explorerId().value(), progress.xp(), progress.level(),
            titlesById.get(progress.levelTitle(policy.policy())).name(),
            curve.threshold(progress.level()), curve.threshold(progress.level() + 1),
            displayTitle == null ? null : new TitleRef(displayTitle, titlesById.get(displayTitle).name()),
            progress.selectedTitle().orElse(null),
            new StreakResponse(streak.asOf(now), streak.lastMonth() == null ? null : streak.lastMonth().toString(),
                streak.activeIn(now)),
            progress.badges().size(),
            rules.badges().stream().map(badge -> new BadgeResponse(badge.id(), badge.ico(), badge.name(), badge.desc(),
                progress.badges().containsKey(badge.id()), progress.badges().get(badge.id()))).toList(),
            rules.titles().stream().map(title -> new TitleResponse(title.id(), title.name(), title.how(), title.source(),
                progress.titles().containsKey(title.id()), title.id().equals(displayTitle))).toList(),
            progress.ledger().recent(10).stream().map(entry -> new XpEntryResponse(entry.source().name(), entry.amount(),
                entry.refId(), entry.at())).toList());
    }
}
