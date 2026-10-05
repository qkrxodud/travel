package com.kobi.territory.progression.api.web;

import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.ProgressionRules.SeasonView;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.catalog.api.query.RewardCalculator;
import com.kobi.territory.catalog.api.query.SeasonLineupQuery;
import com.kobi.territory.catalog.api.query.SeasonLineupQuery.LineupRegionView;
import com.kobi.territory.catalog.api.query.SeasonLineupQuery.SeasonLineupView;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.api.web.ProgressionDtos.NextSeasonResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.SeasonEvidenceResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.SeasonRegionResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.SeasonRoundResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.SeasonsResponse;
import com.kobi.territory.progression.application.CollectionBookService;
import com.kobi.territory.progression.application.CollectionBookService.SeasonOverview;
import com.kobi.territory.progression.domain.collectionbook.SeasonProgress;
import com.kobi.territory.progression.domain.collectionbook.SeasonRound;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계절 한정 테마(9단계, 도감 지도 단위). 그 회차 기간 안에 처리된 체크인만 세고, 기간이 끝나면 미완성 진행은 닫힌다(기록은 남김).
 * 완성하면 완성 시점 지도 멤버 전원이 +XP·칭호·회차 배경을 받는다. 진행 반영은 체크인 소식으로 비동기.
 */
@RestController
public class SeasonController {

    private static final String BACKGROUND_PREFIX = "season:";

    private final CollectionBookService collectionBooks;
    private final ProgressionRules rules;
    private final RegionCatalog regions;
    private final RewardCalculator rewards;
    private final SeasonLineupQuery lineups;

    public SeasonController(CollectionBookService collectionBooks, ProgressionRules rules, RegionCatalog regions,
                            RewardCalculator rewards, SeasonLineupQuery lineups) {
        this.collectionBooks = collectionBooks;
        this.rules = rules;
        this.regions = regions;
        this.rewards = rewards;
        this.lineups = lineups;
    }

    @GetMapping("/seasons/current")
    public SeasonsResponse current(@CurrentExplorer ExplorerId explorerId,
                                   @RequestParam(value = "mapId", required = false) String mapId) {
        SeasonOverview overview = collectionBooks.seasons(explorerId, mapId);
        Map<String, SeasonView> seasons = rules.seasons().stream().collect(Collectors.toMap(SeasonView::id, Function.identity()));
        List<SeasonRoundResponse> current = overview.open().stream()
            .map(round -> response(round, overview, explorerId, seasons, true)).toList();
        List<SeasonRoundResponse> history = overview.closedRounds().stream()
            .map(round -> response(round, overview, explorerId, seasons, false)).toList();
        SeasonRound next = overview.next();
        return new SeasonsResponse(overview.mapId(), overview.now(), current,
            next == null ? null : new NextSeasonResponse(next.roundId(), next.seasonId(), nameOf(next, seasons),
                seasons.get(next.seasonId()).emoji(), next.startsAt(), next.endsAt(), lineupOf(next, seasons).provenance()),
            history);
    }

    private SeasonRoundResponse response(SeasonRound round, SeasonOverview overview, ExplorerId explorerId,
                                         Map<String, SeasonView> seasons, boolean open) {
        SeasonView season = seasons.get(round.seasonId());
        SeasonProgress progress = overview.book().seasonProgressOf(round.roundId());
        Set<RegionCode> collected = progress.collected();
        SeasonLineupView lineup = lineupOf(round, seasons);
        List<SeasonRegionResponse> members = lineup.regions().stream().map(member -> {
            RegionView region = regions.findRegion(RegionCode.of(member.code())).orElse(null);
            return new SeasonRegionResponse(member.code(), region == null ? member.code() : region.name(),
                region == null ? null : region.provinceCode(), collected.contains(RegionCode.of(member.code())), member.provenance(),
                member.evidence().stream().map(item -> new SeasonEvidenceResponse(item.contentId(), item.title(), item.startDate(),
                    item.endDate(), item.fetchedAt(), item.evidenceKind())).toList());
        }).toList();
        long remaining = open ? Math.max(0, Duration.between(overview.now(), round.endsAt()).getSeconds()) : 0;
        return new SeasonRoundResponse(round.roundId(), round.seasonId(), nameOf(round, seasons), season.emoji(), round.year(),
            round.startsAt(), round.endsAt(), remaining, open, progress.have(), round.regions().size(), progress.completed(),
            progress.completedAt(), progress.rewardedTo(explorerId), rewards.seasonComplete().amount(), season.titleId(),
            season.titleName(), BACKGROUND_PREFIX + round.roundId(), members, lineup.provenance(), lineup.source());
    }

    /** 회차 지역 목록과 근거(13s단계 — 확정본, 없으면 기본 목록). 카탈로그가 모르는 회차면 계절 정의의 기본 목록. */
    private SeasonLineupView lineupOf(SeasonRound round, Map<String, SeasonView> seasons) {
        return lineups.lineupOf(round.roundId()).orElseGet(() -> {
            SeasonView season = seasons.get(round.seasonId());
            return new SeasonLineupView(round.roundId(), season.provenance(), null, null, season.regionCodes().stream()
                .map(code -> new LineupRegionView(code, season.provenance(), List.of())).toList());
        });
    }

    private static String nameOf(SeasonRound round, Map<String, SeasonView> seasons) {
        return round.year() + " " + seasons.get(round.seasonId()).name();
    }
}
