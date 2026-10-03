package com.kobi.territory.exploration.application;

import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.ClaimTransferred;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.exploration.api.event.VisitDisputeChanged;
import com.kobi.territory.exploration.api.event.VisitEdited;
import com.kobi.territory.exploration.domain.territory.CancelResult;
import com.kobi.territory.exploration.domain.territory.CheckInContext;
import com.kobi.territory.exploration.domain.territory.ClaimTransfer;
import com.kobi.territory.exploration.domain.policy.CheckInPolicy;
import com.kobi.territory.exploration.domain.territory.CheckInPreview;
import com.kobi.territory.exploration.domain.territory.CheckInResult;
import com.kobi.territory.exploration.domain.map.ExpeditionMapRepository;
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
import com.kobi.territory.exploration.domain.territory.VisitView;
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
 * 쓰기 커맨드(checkIn·edit·cancel·dispute) 동시성 규칙 — MySQL 대응(QA P1-1, 1단계 N2)
 *  1) 트랜잭션은 READ_COMMITTED 다 — 문장마다 최신 커밋을 보므로, 잠금 전에 읽은 것이 있어도 잠금 이후의 방문 읽기는
 *     먼저 커밋된 동시 체크인을 본다(REPEATABLE READ 스냅샷 고정 문제 없음).
 *  2) 멤버 확인을 <b>먼저</b> 한다(애그리거트를 불러오지 않는 존재 확인 — 비멤버는 잠금 없이 403, N2).
 *  3) 지도 행 공유 잠금(FOR SHARE) → territory 행 배타 잠금 순서(QA P1-2). 지도 커맨드(탈퇴 등)의 지도 배타 잠금과 직렬화돼,
 *     탈퇴 커밋 뒤의 체크인은 잠금 뒤 멤버 재확인에서 403, 탈퇴 전 체크인은 탈퇴 시각보다 앞선 처리 시각을 갖는다.
 *     잠금 순서가 언제나 지도 → territory 이고 지도 커맨드는 territory 를 잠그지 않으므로 순환이 없다.
 *     방문(Territory)은 반드시 잠금 뒤에 읽는다.
 *  4) (4단계) territory 잠금 뒤 탐험가 행 공유 잠금으로 활성 여부를 다시 본다 — 로그인 병합(탐험가 행 배타 잠금)과 직렬화돼,
 *     병합이 커밋된 뒤의 체크인은 거절되고 병합 전에 잠근 체크인은 병합이 그 커밋을 기다린다(옮겨질 방문을 놓치지 않는다).
 *     잠금 순서: 지도 S → territory X → 탐험가 S. 병합은 탐험가 X 만 잡으므로 순환이 없다.
 */
@Service
public class CheckInService {

    static final String AGGREGATE = "Territory";

    private final TerritoryRepository territories;
    private final ExpeditionMapRepository maps;
    private final MapAccess mapAccess;
    private final CatalogRegionDirectory regions;
    private final RegionCatalog catalog;
    private final EventOutbox outbox;
    private final ExplorationSettings settings;
    private final Clock clock;

    public CheckInService(TerritoryRepository territories, ExpeditionMapRepository maps, MapAccess mapAccess,
                          CatalogRegionDirectory regions,
                          RegionCatalog catalog, EventOutbox outbox, ExplorationSettings settings, Clock clock) {
        this.territories = territories;
        this.maps = maps;
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
        return outcome(mm.map().id(), CheckInPreview.preview(territory, explorerId, region,
            regions.mysteryRegionAt(clock.instant()), regions));
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
        MapMembership mm = lockAsMember(cmd.explorerId(), MapSelector.of(cmd.mapId()));
        MapId mapId = mm.map().id();
        RegionSnapshot region = regions.require(cmd.regionCode());
        Territory territory = territories.load(mapId);

        Instant now = Optional.ofNullable(visitedAtOverride).orElseGet(clock::instant);
        CheckInPreview.Result preview = CheckInPreview.preview(territory, cmd.explorerId(), region, regions.mysteryRegionAt(now),
            regions);
        CheckInPolicy policy = Optional.ofNullable(policyOverride)
            .orElseGet(() -> mm.map().checkInPolicy(settings.onboardingGrace()));
        CheckInResult result = territory.checkIn(cmd.explorerId(), region, VisitDate.of(cmd.visitDate()),
            Memo.of(cmd.memo()), PhotoRef.ofNullable(cmd.photoUrl()), context(policy, mm, now));
        territories.save(territory);

        outbox.append(AGGREGATE, mapId.value(), regionVisited(result, mm.map().memberIds()));
        return new CheckInOutcome(result, territory.viewOf(result.visit(), cmd.explorerId()), outcome(mapId, preview));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EditOutcome edit(EditVisitCommand cmd) {
        MapMembership mm = lockAsMember(cmd.explorerId(), MapSelector.of(cmd.mapId()));
        MapId mapId = mm.map().id();
        Territory territory = territories.load(mapId);

        Visit edited = territory.editVisit(cmd.explorerId(), cmd.regionCode(),
            VisitPatch.of(cmd.visitDate(), cmd.memo(), cmd.photoUrl()),
            context(mm.map().checkInPolicy(settings.onboardingGrace()), mm, clock.instant()));
        territories.save(territory);

        outbox.append(AGGREGATE, mapId.value(), new VisitEdited(cmd.explorerId().value(), mapId.value(),
            cmd.regionCode().value(), edited.visitDate().value(), !edited.memo().isEmpty(), edited.photo() != null,
            clock.instant()));
        return new EditOutcome(mapId, territory.viewOf(edited, cmd.explorerId()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CancelResult cancel(ExplorerId explorerId, String mapIdOrNull, RegionCode code) {
        MapId mapId = lockAsMember(explorerId, MapSelector.of(mapIdOrNull)).map().id();
        Territory territory = territories.load(mapId);

        CancelResult result = territory.cancelVisit(explorerId, code);
        territories.save(territory);

        Instant now = clock.instant();
        RegionSnapshot region = result.visit().region();
        outbox.append(AGGREGATE, mapId.value(), new VisitCancelled(explorerId.value(), mapId.value(), code.value(),
            region.rarity(), region.provinceCode(), result.wasClaim(), result.remaining(), result.regionStillOnMap(),
            now, result.visit().generation()));
        result.transferredClaim().ifPresent(transfer -> outbox.append(AGGREGATE, mapId.value(),
            claimTransferred(mapId, transfer, now)));
        return result;
    }

    /** 지도장의 방문 이의 표시/해제 — 멤버 확인(잠금 전) → territory 잠금 → 지도장 확인 → 방문 플래그. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VisitView dispute(ExplorerId requester, MapId mapId, RegionCode code, ExplorerId member, boolean disputed) {
        MapMembership mm = lockAsMember(requester, MapSelector.of(mapId));
        mm.map().requireOwner(requester);
        Territory territory = territories.load(mapId);

        Visit visit = territory.dispute(code, member, disputed);
        territories.save(territory);

        outbox.append(AGGREGATE, mapId.value(), new VisitDisputeChanged(mapId.value(), code.value(), member.value(), disputed,
            requester.value(), clock.instant()));
        return territory.viewOf(visit, requester);
    }

    /**
     * 요청한 탐험가가 멤버인 지도의 territory 행을 잠근다. 개인 지도는 잠금이 첫 문장(lockPersonal)이고, mapId 지정은
     * 멤버 확인을 먼저 해 비멤버가 남의 지도 행을 잠그지 못하게 한다(N2). 잠글 행이 없으면 원인(탐험가/지도 없음)을 낸다.
     */
    private MapMembership lockAsMember(ExplorerId explorerId, MapSelector selector) {
        MapId mapId = mapAccess.mapIdOf(explorerId, selector);
        mapAccess.requireMembership(explorerId, mapId);              // 비멤버는 잠그기 전에 거른다(N2)
        if (!maps.lockShared(mapId) || !territories.lock(mapId)) throw selector.notFound(); // 지도 S → territory X
        mapAccess.requireActiveLocked(explorerId);                   // → 탐험가 S: 그사이 커밋된 병합(로그인)이면 404(4단계)
        return mapAccess.resolve(explorerId, MapSelector.of(mapId)); // 잠금 뒤 다시 확인 — 그사이 커밋된 탈퇴면 403
    }

    private CheckInContext context(CheckInPolicy policy, MapMembership mm, Instant now) {
        return new CheckInContext(policy, mm.member().joinedAt(), now, clock.getZone());
    }

    /**
     * 체크인 결과 → 공개 이벤트. 재계산 배치의 이력 재생(TerritoryQuery.visitHistory)도 같은 변환을 쓴다.
     * @param memberIds 체크인 시점 지도 멤버(테마 완성 수령자 — 결정 1)
     */
    static RegionVisited regionVisited(CheckInResult result, List<ExplorerId> memberIds) {
        Visit visit = result.visit();
        RegionSnapshot region = visit.region();
        return new RegionVisited(visit.checkedInBy().value(), result.mapId().value(), region.code().value(), region.rarity(),
            region.provinceCode(), visit.visitedAt(), visit.visitDate().value(), result.facts().firstInProvince(), result.facts().nth(),
            result.facts().firstClaim(), visit.generation(), memberIds.stream().map(ExplorerId::value).toList());
    }

    /** 선점 이전 → 공개 이벤트(새 선점자 +10 XP). 탈퇴 처리(TerritoryMembershipService)도 같은 변환을 쓴다. */
    static ClaimTransferred claimTransferred(MapId mapId, ClaimTransfer transfer, Instant at) {
        RegionSnapshot region = transfer.region();
        return new ClaimTransferred(mapId.value(), region.code().value(), region.rarity(), region.provinceCode(),
            transfer.from().value(), transfer.to().value(), transfer.reason().name(), at);
    }

    private PreviewOutcome outcome(MapId mapId, CheckInPreview.Result preview) {
        List<ItemView> items = preview.itemIds().stream().map(catalog::item).flatMap(Optional::stream).toList();
        return new PreviewOutcome(mapId, preview, items, catalog.findRegion(preview.region().code()).orElseThrow());
    }

    public record PreviewOutcome(MapId mapId, CheckInPreview.Result preview, List<ItemView> items, RegionView region) {}

    /** @param view 요청한 탐험가가 보는 방문(응답용) */
    public record CheckInOutcome(CheckInResult result, VisitView view, PreviewOutcome preview) {}

    public record EditOutcome(MapId mapId, VisitView view) {}
}
