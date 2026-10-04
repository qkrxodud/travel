package com.kobi.territory.progression.api.web;

import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.ProgressionRules.TitleView;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.catalog.api.query.ProvinceView;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.progression.api.web.ProgressionDtos.BadgeResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.FreezeUseResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.MilestoneResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.MonthlyFreezeResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.ProvinceProgressResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.StreakFreezeResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.ProgressResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.SelectTitleRequest;
import com.kobi.territory.progression.api.web.ProgressionDtos.StreakResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.TitleRef;
import com.kobi.territory.progression.api.web.ProgressionDtos.TitleResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.XpEntryResponse;
import com.kobi.territory.progression.application.ProgressService;
import com.kobi.territory.progression.application.ProgressionCatalog;
import com.kobi.territory.progression.domain.progress.ExplorerProgress;
import com.kobi.territory.progression.domain.progress.MilestoneStatus;
import com.kobi.territory.progression.domain.progress.MonthlyFreezeProgress;
import com.kobi.territory.progression.domain.policy.LevelCurve;
import com.kobi.territory.progression.domain.progress.Streak;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final Map<String, String> provinceNames;

    public ProgressController(ProgressService progressService, ProgressionCatalog policy, ProgressionRules rules,
                              RegionCatalog regions) {
        this.progressService = progressService;
        this.policy = policy;
        this.rules = rules;
        this.provinceNames = regions.provinces().stream()
            .collect(Collectors.toMap(ProvinceView::code, ProvinceView::name, (first, second) -> first, LinkedHashMap::new));
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
        int displayMonths = progress.streakMonthsAsOf(now);
        Function<MilestoneStatus, MilestoneResponse> milestone = status -> new MilestoneResponse(status.months(), status.xp(),
            status.freezes(), status.titleId(), status.titleId() == null ? null : titlesById.get(status.titleId()).name(),
            status.reached(), status.reachedAt(), status.remainingMonths());
        MonthlyFreezeProgress thisMonth = progress.monthlyFreezeProgress(policy.policy(), now);
        return new ProgressResponse(progress.explorerId().value(), progress.xp(), progress.level(),
            titlesById.get(progress.levelTitle(policy.policy())).name(),
            curve.threshold(progress.level()), curve.threshold(progress.level() + 1),
            displayTitle == null ? null : new TitleRef(displayTitle, titlesById.get(displayTitle).name()),
            progress.selectedTitle().orElse(null),
            new StreakResponse(displayMonths, streak.lastMonth() == null ? null : streak.lastMonth().toString(),
                streak.activeIn(now), streak.emptyMonthsBefore(now),
                progress.frozenMonthsAsOf(now).stream().map(YearMonth::toString).toList()),
            progress.badges().size(),
            rules.badges().stream().map(badge -> new BadgeResponse(badge.id(), badge.ico(), badge.name(), badge.desc(),
                progress.badges().containsKey(badge.id()), progress.badges().get(badge.id()))).toList(),
            rules.titles().stream().map(title -> new TitleResponse(title.id(), title.name(), title.how(), title.source(),
                progress.titles().containsKey(title.id()), title.id().equals(displayTitle))).toList(),
            progress.ledger().recent(10).stream().map(entry -> new XpEntryResponse(entry.source().name(), entry.amount(),
                entry.refId(), entry.at())).toList(),
            new StreakFreezeResponse(progress.freezes().held(), policy.policy().streakRules().freezeMaxHeld(),
                progress.freezes().lastUse().map(use -> new FreezeUseResponse(use.month().toString(), -use.amount(), use.at()))
                    .orElse(null),
                new MonthlyFreezeResponse(thisMonth.period(), thisMonth.questsRewarded(), thisMonth.questsRequired(),
                    thisMonth.reward(), thisMonth.earned(), thisMonth.granted())),
            progress.nextMilestone(policy.policy(), now).map(milestone).orElse(null),
            progress.milestoneStatus(policy.policy(), now).stream().map(milestone).toList(),
            progress.provinceCoverage(policy.policy()).stream().map(coverage -> new ProvinceProgressResponse(
                coverage.provinceCode(), provinceNames.get(coverage.provinceCode()), coverage.covered(), coverage.total(),
                coverage.percent(), coverage.complete(), coverage.conquered(), coverage.conqueredAt())).toList(),
            progress.mysteryFoundCount(), progress.revisitStampCount(), progress.wishesFulfilledCount());
    }
}
