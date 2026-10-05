package com.kobi.territory.catalog.api.web;

import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.catalog.api.web.AdminSeasonDtos.AttemptResponse;
import com.kobi.territory.catalog.api.web.AdminSeasonDtos.CandidateResponse;
import com.kobi.territory.catalog.api.web.AdminSeasonDtos.EvidenceResponse;
import com.kobi.territory.catalog.api.web.AdminSeasonDtos.LineupRegionResponse;
import com.kobi.territory.catalog.api.web.AdminSeasonDtos.LineupResponse;
import com.kobi.territory.catalog.api.web.AdminSeasonDtos.RoundLineupResponse;
import com.kobi.territory.catalog.api.web.AdminSeasonDtos.ScheduleResponse;
import com.kobi.territory.catalog.api.web.AdminSeasonDtos.SeasonLineupsResponse;
import com.kobi.territory.catalog.api.web.AdminSeasonDtos.TourApiStatusResponse;
import com.kobi.territory.catalog.application.SeasonLineupOverview;
import com.kobi.territory.catalog.application.SeasonLineupService;
import com.kobi.territory.catalog.application.SeasonLineupStatus;
import com.kobi.territory.catalog.domain.definition.SeasonRoundWindow;
import com.kobi.territory.catalog.domain.lineup.CollectionAttempt;
import com.kobi.territory.catalog.domain.lineup.CollectionSchedule;
import com.kobi.territory.catalog.domain.lineup.FetchFailure;
import com.kobi.territory.catalog.domain.lineup.FetchUsage;
import com.kobi.territory.catalog.domain.lineup.LineupProvenance;
import com.kobi.territory.catalog.domain.lineup.LineupRegions;
import com.kobi.territory.catalog.domain.lineup.LineupSnapshot;
import com.kobi.territory.catalog.domain.lineup.SeasonLineup;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 계절 회차 지역 목록(13s단계). {@code X-Admin-Token} 검사는 조립 모듈의 /admin/** 인터셉터가 한다.
 * <ul>
 *   <li>{@code GET /admin/seasons} — TourAPI 연결 현황 + 지금 열린 회차·계절마다 다음 회차의 쓰는 목록·후보·마지막 시도·경고</li>
 *   <li>{@code GET /admin/seasons/{roundId}} — 회차 하나(지난 회차도 — 그때 쓴 목록과 근거)</li>
 *   <li>{@code POST /admin/seasons/{roundId}/refresh?fresh=} — 지금 모아 후보(미리보기)로. fresh=true 면 응답 캐시를 건너뛴다. 실패해도 200 — 까닭은
 *       lastAttempt·warnings(지금 목록은 그대로). 열린 회차 409 SEASON_ROUND_LOCKED, 모르는 회차 404 SEASON_ROUND_NOT_FOUND</li>
 *   <li>{@code PUT /admin/seasons/{roundId}/confirm} — 후보를 확정(관리자). 후보 없음 409 SEASON_CANDIDATE_MISSING</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin/seasons")
public class AdminSeasonController {

    private final SeasonLineupService lineups;
    private final RegionCatalog regions;

    public AdminSeasonController(SeasonLineupService lineups, RegionCatalog regions) {
        this.lineups = lineups;
        this.regions = regions;
    }

    @GetMapping
    public SeasonLineupsResponse overview() {
        SeasonLineupOverview overview = lineups.overview();
        FetchUsage usage = overview.usage();
        CollectionSchedule schedule = overview.schedule();
        return new SeasonLineupsResponse(
            new TourApiStatusResponse(usage.configured(), usage.callsToday(), usage.dailyLimit(), usage.exhausted(),
                LineupProvenance.TOURAPI_SOURCE, usageWarnings(usage)),
            new ScheduleResponse(schedule.leadDays(), schedule.recollectAfter().toHours(), schedule.autoConfirm(),
                schedule.autoConfirmMinRegions()),
            overview.rounds().stream().map(status -> response(status, usage.configured(), schedule)).toList());
    }

    @GetMapping("/{roundId}")
    public RoundLineupResponse round(@PathVariable("roundId") String roundId) {
        return response(lineups.status(roundId));
    }

    @PostMapping("/{roundId}/refresh")
    public RoundLineupResponse refresh(@PathVariable("roundId") String roundId,
                                       @RequestParam(value = "fresh", defaultValue = "false") boolean fresh) {
        return response(lineups.refresh(roundId, fresh));
    }

    @PutMapping("/{roundId}/confirm")
    public RoundLineupResponse confirm(@PathVariable("roundId") String roundId) {
        return response(lineups.confirm(roundId));
    }

    private RoundLineupResponse response(SeasonLineupStatus status) {
        return response(status, lineups.configured(), lineups.schedule());
    }

    private RoundLineupResponse response(SeasonLineupStatus status, boolean configured, CollectionSchedule schedule) {
        SeasonLineup lineup = status.lineup();
        SeasonRoundWindow window = lineup.window();
        LineupSnapshot candidate = lineup.candidate();
        CollectionAttempt attempt = lineup.lastAttempt();
        List<String> warnings = new ArrayList<>();
        if (!configured && !status.locked()) warnings.add(FetchFailure.NOT_CONFIGURED.message() + " — AI 추정 목록을 그대로 씁니다");
        warnings.addAll(lineup.alerts());
        return new RoundLineupResponse(window.roundId(), window.seasonId(), window.year() + " " + status.season().name(),
            status.season().emoji(), window.year(), window.startsAt(), window.endsAt(), status.locked(),
            window.collectionOpensAt(schedule.leadDays()), status.nextPlan().name(),
            new LineupResponse(status.inEffect().provenance(), source(status.inEffect()),
                lineup.confirmedBy() == null ? null : lineup.confirmedBy().name(), lineup.confirmedAt(),
                lineup.confirmed() == null ? null : lineup.confirmed().collectedAt(), regions(status.inEffect())),
            candidate == null ? null : new CandidateResponse(candidate.regions().provenance(), source(candidate.regions()),
                candidate.collectedAt(), candidate.regions().count(LineupProvenance.TOURAPI), regions(candidate.regions()),
                candidate.warnings()),
            attempt == null ? null : new AttemptResponse(attempt.at(), attempt.outcome().name(), attempt.failed(), attempt.warnings()),
            List.copyOf(warnings));
    }

    private static List<String> usageWarnings(FetchUsage usage) {
        List<String> warnings = new ArrayList<>();
        if (usage.settingsProblem() != null) warnings.add(usage.settingsProblem() + " — 연동을 끄고 AI 추정 목록을 그대로 씁니다");
        else if (!usage.configured()) warnings.add(FetchFailure.NOT_CONFIGURED.message() + " — 계절 회차는 AI 추정 목록을 그대로 씁니다");
        if (usage.configured() && usage.exhausted()) {
            warnings.add("오늘 TourAPI 호출 상한(" + usage.dailyLimit() + "회)에 닿아 내일까지 호출하지 않습니다(같은 날 캐시된 응답은 씁니다)");
        }
        return List.copyOf(warnings);
    }

    private static String source(LineupRegions lineupRegions) {
        return lineupRegions.hasEvidence() ? LineupProvenance.TOURAPI_SOURCE : null;
    }

    private List<LineupRegionResponse> regions(LineupRegions lineupRegions) {
        return lineupRegions.stream().map(region -> {
            RegionView view = regions.findRegion(region.code()).orElse(null);
            return new LineupRegionResponse(region.code().value(), view == null ? null : view.name(),
                view == null ? null : view.provinceCode(), region.provenance().code(),
                region.evidence().stream().map(item -> new EvidenceResponse(item.contentId(), item.title(), item.startDate(), item.endDate(),
                    item.fetchedAt(), item.kind().name())).toList());
        }).toList();
    }
}
