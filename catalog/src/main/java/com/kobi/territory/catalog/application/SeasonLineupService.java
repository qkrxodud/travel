package com.kobi.territory.catalog.application;

import com.kobi.territory.catalog.api.query.SeasonLineupQuery;
import com.kobi.territory.catalog.domain.CatalogError;
import com.kobi.territory.catalog.domain.catalog.Catalog;
import com.kobi.territory.catalog.domain.catalog.CatalogRepository;
import com.kobi.territory.catalog.domain.definition.ProgressionDefinitions;
import com.kobi.territory.catalog.domain.definition.SeasonDefinition;
import com.kobi.territory.catalog.domain.definition.SeasonRoundWindow;
import com.kobi.territory.catalog.domain.lineup.CollectionPlan;
import com.kobi.territory.catalog.domain.lineup.CollectionSchedule;
import com.kobi.territory.catalog.domain.lineup.ConfirmedBy;
import com.kobi.territory.catalog.domain.lineup.FestivalFetch;
import com.kobi.territory.catalog.domain.lineup.FestivalSource;
import com.kobi.territory.catalog.domain.lineup.LineupProvenance;
import com.kobi.territory.catalog.domain.lineup.LineupRegions;
import com.kobi.territory.catalog.domain.lineup.LineupSelector;
import com.kobi.territory.catalog.domain.lineup.SeasonLineup;
import com.kobi.territory.catalog.domain.lineup.SeasonLineupRepository;
import com.kobi.territory.catalog.domain.region.RegionLocator;
import com.kobi.territory.common.error.TerritoryException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 계절 회차 지역 목록(13s단계) — 공개 Query({@link SeasonLineupQuery}) 구현과 관리자 갱신·확정, 자동 수집. 판단(고정·수집 시점·자동 확정·후보
 * 고르기)은 도메인(SeasonLineup·LineupSelector·RegionLocator)이 하고, 여기는 불러오기 → 바깥 자료 읽기(트랜잭션 밖) → 도메인에 시키기 → 저장 순서만
 * 둔다. 바깥 호출은 오래 걸릴 수 있어 트랜잭션 밖에서 하고, 저장 때 다시 불러 도메인이 고정 여부를 다시 확인한다.
 */
@Service
public class SeasonLineupService implements SeasonLineupQuery {

    private static final Logger log = LoggerFactory.getLogger(SeasonLineupService.class);

    private final ProgressionDefinitions definitions;
    private final RegionLocator locator;
    private final SeasonLineupRepository lineups;
    private final FestivalSource festivals;
    private final SeasonLineupSettings settings;
    private final SeasonLineupCache cache;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final TransactionTemplate separately;

    public SeasonLineupService(CatalogRepository catalogs, SeasonLineupRepository lineups, FestivalSource festivals,
                               SeasonLineupSettings settings, SeasonLineupCache cache, Clock clock,
                               PlatformTransactionManager transactionManager) {
        Catalog catalog = catalogs.load();
        this.definitions = catalog.progression();
        this.locator = catalog.regionLocator(settings.boundaryToleranceKilometers());
        this.lineups = lineups;
        this.festivals = festivals;
        this.settings = settings;
        this.cache = cache;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.separately = new TransactionTemplate(transactionManager);
        this.separately.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---- 공개 Query ----

    /** 회차 지역 목록. 열렸는데 확정본이 없는 회차는 이때 기본 목록으로 고정(스냅숏)해 둔다 — 처음 조회 시. */
    @Override
    public Optional<SeasonLineupView> lineupOf(String roundId) {
        return cache.get(roundId, () -> definitions.seasonOfRound(roundId).flatMap(season -> definitions.roundWindow(roundId, clock.getZone())
            .map(window -> view(roundId, current(window, season), season))));
    }

    /** 지금 열린 회차를 모두 스냅숏으로 고정한다(기동 직후·하루 한 번 — 키와 무관). */
    public void snapshotOpenRounds() {
        Instant now = clock.instant();
        definitions.openWindows(now, clock.getZone()).forEach(window -> current(window, season(window.roundId())));
    }

    /** 저장된 회차 기록(없으면 빈 기록). 열렸는데 확정본이 없으면 따로 커밋되는 트랜잭션에서 스냅숏으로 고정한 뒤 다시 읽는다. */
    private SeasonLineup current(SeasonRoundWindow window, SeasonDefinition season) {
        SeasonLineup lineup = lineups.find(window.roundId()).orElseGet(() -> SeasonLineup.start(window));
        if (!lineup.awaitingSnapshotAt(clock.instant())) return lineup;
        try {
            separately.executeWithoutResult(status -> {
                SeasonLineup fresh = lineups.find(window.roundId()).orElseGet(() -> SeasonLineup.start(window));
                if (fresh.freezeOpened(season.regions(), clock.instant())) lineups.save(fresh);
            });
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException concurrent) {
            // 다른 쪽이 먼저 고정했다 — 다시 읽는다
        }
        return lineups.find(window.roundId()).orElse(lineup);
    }

    private static SeasonLineupView view(String roundId, SeasonLineup lineup, SeasonDefinition season) {
        LineupRegions regions = lineup.inEffect(season.regions());
        return new SeasonLineupView(roundId, regions.provenance(), regions.hasEvidence() ? LineupProvenance.TOURAPI_SOURCE : null,
            lineup.confirmedAt(), regions.stream().map(region -> new LineupRegionView(region.code().value(), region.provenance().code(),
                region.evidence().stream().map(item -> new EvidenceView(item.contentId(), item.title(), item.startDate(),
                    item.endDate(), item.fetchedAt(), item.kind().name())).toList())).toList());
    }

    // ---- 관리자 ----

    /** 지금 열린 회차와 계절마다 다음 회차. */
    public SeasonLineupOverview overview() {
        Instant now = clock.instant();
        List<SeasonRoundWindow> windows = Stream.concat(definitions.openWindows(now, clock.getZone()).stream(),
            definitions.upcomingWindows(now, clock.getZone()).stream()).toList();
        return new SeasonLineupOverview(festivals.usage(), settings.schedule(), windows.stream()
            .map(window -> status(current(window, season(window.roundId())), now)).toList());
    }

    public SeasonLineupStatus status(String roundId) {
        SeasonLineup lineup = loadOrStart(roundId);
        return status(current(lineup.window(), season(roundId)), clock.instant());
    }

    /** 지금 모은다(후보 미리보기) — 확정은 따로. 열린 회차는 SEASON_ROUND_LOCKED. */
    public SeasonLineupStatus refresh(String roundId, boolean bypassCache) {
        SeasonLineup current = loadOrStart(roundId);
        current.requireChangeableAt(clock.instant());
        FestivalFetch fetch = fetch(current.window(), bypassCache);
        SeasonLineup saved = writing(roundId, lineup -> lineup.record(fetch, selector(roundId), clock.instant()));
        return status(saved, clock.instant());
    }

    /** 후보를 확정한다(관리자). */
    public SeasonLineupStatus confirm(String roundId) {
        SeasonLineup saved = writing(roundId, lineup -> lineup.confirm(ConfirmedBy.ADMIN, clock.instant()));
        return status(saved, clock.instant());
    }

    // ---- 자동 수집(스케줄·기동 직후) ----

    /** 키가 있으면 계절마다 다음 회차를 계획대로 모으고(필요하면 자동 확정) 회차 id 별 결과를 돌려준다. 키가 없으면 아무것도 안 한다. */
    public Map<String, CollectionPlan> collectAutomatically() {
        if (!festivals.configured()) return Map.of();
        Instant now = clock.instant();
        Map<String, CollectionPlan> done = new LinkedHashMap<>();
        for (SeasonRoundWindow window : definitions.upcomingWindows(now, clock.getZone())) {
            CollectionPlan plan = loadOrStart(window.roundId()).planAt(now, settings.schedule());
            if (plan == CollectionPlan.NONE) continue;
            try {
                FestivalFetch fetch = fetch(window, false);
                writing(window.roundId(), lineup -> {
                    lineup.record(fetch, selector(window.roundId()), clock.instant());
                    lineup.confirmAutomatically(plan, settings.schedule(), clock.instant());
                });
                done.put(window.roundId(), plan);
            } catch (TerritoryException refused) {
                log.warn("계절 회차 {} 자동 수집 건너뜀: {}", window.roundId(), refused.getMessage());
            } catch (RuntimeException unexpected) {
                // 메시지에 바깥 요청 주소(키)가 들 수 있어 종류 이름만 남긴다
                log.warn("계절 회차 {} 자동 수집 건너뜀: {}", window.roundId(), unexpected.getClass().getSimpleName());
            }
        }
        if (!done.isEmpty()) log.info("계절 회차 자동 수집: {}", done);
        return done;
    }

    public boolean configured() {
        return festivals.configured();
    }

    public CollectionSchedule schedule() {
        return settings.schedule();
    }

    // ---- 내부 ----

    private SeasonLineupStatus status(SeasonLineup lineup, Instant now) {
        SeasonDefinition season = season(lineup.roundId());
        return new SeasonLineupStatus(season, lineup, lineup.inEffect(season.regions()), lineup.lockedAt(now),
            lineup.planAt(now, settings.schedule()));
    }

    /** 회차 기간의 축제 → 계절 관광지(키워드마다 한 번). 하나라도 실패하면 거기서 멈추고 그 실패. */
    private FestivalFetch fetch(SeasonRoundWindow window, boolean bypassCache) {
        int margin = settings.policy().marginDays();
        FestivalFetch fetch = festivals.festivalsBetween(window.searchFrom(margin), window.searchUntil(margin), bypassCache);
        for (String keyword : season(window.roundId()).attractionKeywords()) {
            if (fetch instanceof FestivalFetch.Failed) break;
            fetch = FestivalFetch.combine(fetch, festivals.attractionsMatching(keyword, bypassCache));
        }
        return fetch;
    }

    private LineupSelector selector(String roundId) {
        return new LineupSelector(season(roundId), locator, settings.policy());
    }

    private SeasonDefinition season(String roundId) {
        return definitions.seasonOfRound(roundId).orElseThrow(() -> CatalogError.SEASON_ROUND_NOT_FOUND.exception(roundId));
    }

    private SeasonLineup loadOrStart(String roundId) {
        SeasonRoundWindow window = definitions.roundWindow(roundId, clock.getZone())
            .orElseThrow(() -> CatalogError.SEASON_ROUND_NOT_FOUND.exception(roundId));
        return lineups.find(roundId).orElseGet(() -> SeasonLineup.start(window));
    }

    /** 한 트랜잭션에서 다시 불러 도메인에 시키고 저장한다. 같은 회차를 동시에 고치면 SEASON_LINEUP_BUSY. 커밋 뒤 캐시를 비운다. */
    private SeasonLineup writing(String roundId, Consumer<SeasonLineup> change) {
        try {
            SeasonLineup saved = transactions.execute(status -> {
                SeasonLineup lineup = loadOrStart(roundId);
                change.accept(lineup);
                lineups.save(lineup);
                return lineup;
            });
            cache.invalidate();
            return lineups.find(roundId).orElse(saved);
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException concurrent) {
            throw CatalogError.SEASON_LINEUP_BUSY.exception(roundId);
        }
    }
}
