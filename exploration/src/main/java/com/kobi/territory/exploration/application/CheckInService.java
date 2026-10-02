package com.kobi.territory.exploration.application;

import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.exploration.api.event.VisitEdited;
import com.kobi.territory.exploration.domain.territory.CancelResult;
import com.kobi.territory.exploration.domain.territory.CheckInContext;
import com.kobi.territory.exploration.domain.map.CheckInPolicy;
import com.kobi.territory.exploration.domain.territory.CheckInPreview;
import com.kobi.territory.exploration.domain.territory.CheckInResult;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.MapSelector;
import com.kobi.territory.exploration.domain.territory.Memo;
import com.kobi.territory.exploration.domain.territory.PhotoRef;
import com.kobi.territory.exploration.domain.territory.RegionSnapshot;
import com.kobi.territory.exploration.domain.territory.Territory;
import com.kobi.territory.exploration.domain.territory.TerritoryRepository;
import com.kobi.territory.exploration.domain.territory.Visit;
import com.kobi.territory.exploration.domain.territory.VisitDate;
import com.kobi.territory.exploration.domain.territory.VisitPatch;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체크인 유스케이스 — "잠그고 → 불러와서 → 도메인에 시키고 → 저장 → outbox". 판단·계산은 Territory(Visits)·
 * ExpeditionMap(Members)·CheckInPreview 가 한다. ExpeditionMap은 멤버 여부·설정만 읽는다(수정하지 않음).
 *
 * 쓰기 커맨드(checkIn·edit·cancel) 동시성 규칙 — MySQL REPEATABLE READ 대응(QA P1-1)
 *  1) 트랜잭션 첫 문장이 territory 행 잠금(SELECT ... FOR UPDATE)이다. 잠금 전에 일반 SELECT를 하면
 *     InnoDB 스냅샷이 그 시점에 고정돼, 잠금을 얻은 뒤에도 먼저 커밋된 동시 체크인을 못 본다.
 *  2) 추가 방어로 READ_COMMITTED — 잠금 이후의 읽기는 항상 최신 커밋을 본다.
 */
@Service
public class CheckInService {

    static final String AGGREGATE = "Territory";

    private final TerritoryRepository territories;
    private final MapAccess mapAccess;
    private final CatalogRegionDirectory regions;
    private final RegionCatalog catalog;
    private final EventOutbox outbox;
    private final ExplorationSettings settings;
    private final Clock clock;

    public CheckInService(TerritoryRepository territories, MapAccess mapAccess, CatalogRegionDirectory regions,
                          RegionCatalog catalog, EventOutbox outbox, ExplorationSettings settings, Clock clock) {
        this.territories = territories;
        this.mapAccess = mapAccess;
        this.regions = regions;
        this.catalog = catalog;
        this.outbox = outbox;
        this.settings = settings;
        this.clock = clock;
    }

    /** 체크인 모달용 미리보기(순수 계산, 저장 없음). */
    @Transactional(readOnly = true)
    public PreviewOutcome preview(ExplorerId explorerId, String mapId, RegionCode code) {
        MapMembership mm = mapAccess.resolve(explorerId, MapSelector.of(mapId));
        RegionSnapshot region = regions.require(code);
        Territory territory = territories.load(mm.map().id());
        return outcome(mm.map().id(), CheckInPreview.preview(territory, explorerId, region, regions));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckInOutcome checkIn(CheckInCommand cmd) {
        return checkIn(cmd, null, null);
    }

    /**
     * 개발용 시드 전용(같은 패키지에서만 호출): policyOverride 로 상한을 우회하고, visitedAtOverride 로 처리 시각을
     * 샘플 날짜로 둔다(D6 — 스트릭·월간 퀘스트가 프로토타입과 비슷한 그림이 나오게). 둘 다 null 이면 일반 체크인.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    CheckInOutcome checkIn(CheckInCommand cmd, CheckInPolicy policyOverride, Instant visitedAtOverride) {
        MapId mapId = lockTerritory(cmd.explorerId(), MapSelector.of(cmd.mapId())); // 반드시 첫 문장
        MapMembership mm = mapAccess.resolve(cmd.explorerId(), MapSelector.of(mapId));
        RegionSnapshot region = regions.require(cmd.regionCode());
        Territory territory = territories.load(mapId);

        CheckInPreview.Result preview = CheckInPreview.preview(territory, cmd.explorerId(), region, regions);
        CheckInPolicy policy = Optional.ofNullable(policyOverride)
            .orElseGet(() -> mm.map().checkInPolicy(settings.onboardingGrace()));
        Instant now = Optional.ofNullable(visitedAtOverride).orElseGet(clock::instant);
        CheckInResult result = territory.checkIn(cmd.explorerId(), region, VisitDate.of(cmd.visitDate()),
            Memo.of(cmd.memo()), PhotoRef.ofNullable(cmd.photoUrl()), context(policy, mm, now));
        territories.save(territory);

        outbox.append(AGGREGATE, mapId.value(), regionVisited(result));
        return new CheckInOutcome(result, outcome(mapId, preview));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EditOutcome edit(EditVisitCommand cmd) {
        MapId mapId = lockTerritory(cmd.explorerId(), MapSelector.of(cmd.mapId())); // 반드시 첫 문장
        MapMembership mm = mapAccess.resolve(cmd.explorerId(), MapSelector.of(mapId));
        Territory territory = territories.load(mapId);

        Visit edited = territory.editVisit(cmd.explorerId(), cmd.regionCode(),
            VisitPatch.of(cmd.visitDate(), cmd.memo(), cmd.photoUrl()),
            context(mm.map().checkInPolicy(settings.onboardingGrace()), mm, clock.instant()));
        territories.save(territory);

        outbox.append(AGGREGATE, mapId.value(), new VisitEdited(cmd.explorerId().value(), mapId.value(),
            cmd.regionCode().value(), edited.visitDate().value(), !edited.memo().isEmpty(), edited.photo() != null,
            clock.instant()));
        return new EditOutcome(mapId, edited);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CancelResult cancel(ExplorerId explorerId, String mapIdOrNull, RegionCode code) {
        MapId mapId = lockTerritory(explorerId, MapSelector.of(mapIdOrNull)); // 반드시 첫 문장
        mapAccess.resolve(explorerId, MapSelector.of(mapId));
        Territory territory = territories.load(mapId);

        CancelResult result = territory.cancelVisit(explorerId, code);
        territories.save(territory);

        RegionSnapshot region = result.visit().region();
        outbox.append(AGGREGATE, mapId.value(), new VisitCancelled(explorerId.value(), mapId.value(), code.value(),
            region.rarity(), region.provinceCode(), result.wasClaim(), result.remaining(), result.regionStillOnMap(),
            clock.instant()));
        return result;
    }

    /** 대상 지도의 territory 행을 잠그고 mapId를 돌려준다. 잠글 행이 없을 때만 원인(탐험가/지도 없음)을 조회한다. */
    private MapId lockTerritory(ExplorerId explorerId, MapSelector selector) {
        return territories.lock(selector, explorerId).orElseThrow(() -> {
            mapAccess.requireExplorer(explorerId);
            return selector.notFound();
        });
    }

    private CheckInContext context(CheckInPolicy policy, MapMembership mm, Instant now) {
        return new CheckInContext(policy, mm.member().joinedAt(), now, clock.getZone());
    }

    /** 체크인 결과 → 공개 이벤트. 재계산 배치의 이력 재생(TerritoryQuery.visitHistory)도 같은 변환을 쓴다. */
    static RegionVisited regionVisited(CheckInResult result) {
        Visit visit = result.visit();
        RegionSnapshot region = visit.region();
        return new RegionVisited(visit.checkedInBy().value(), result.mapId().value(), region.code().value(), region.rarity(),
            region.provinceCode(), visit.visitedAt(), visit.visitDate().value(), result.facts().firstInProvince(), result.facts().nth(),
            result.facts().firstClaim());
    }

    private PreviewOutcome outcome(MapId mapId, CheckInPreview.Result preview) {
        List<ItemView> items = preview.itemIds().stream().map(catalog::item).flatMap(Optional::stream).toList();
        return new PreviewOutcome(mapId, preview, items, catalog.findRegion(preview.region().code()).orElseThrow());
    }

    public record PreviewOutcome(MapId mapId, CheckInPreview.Result preview, List<ItemView> items, RegionView region) {}

    public record CheckInOutcome(CheckInResult result, PreviewOutcome preview) {}

    public record EditOutcome(MapId mapId, Visit visit) {}
}
