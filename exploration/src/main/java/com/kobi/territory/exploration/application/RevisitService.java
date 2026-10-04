package com.kobi.territory.exploration.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.event.RecalculationRequests;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.ExplorerMerged;
import com.kobi.territory.exploration.api.event.RevisitStamped;
import com.kobi.territory.exploration.api.query.RevisitQuery;
import com.kobi.territory.exploration.domain.map.MapSelector;
import com.kobi.territory.exploration.domain.revisit.RevisitStamp;
import com.kobi.territory.exploration.domain.revisit.StampBook;
import com.kobi.territory.exploration.domain.revisit.StampBookRepository;
import com.kobi.territory.exploration.domain.revisit.StampEligibility;
import com.kobi.territory.exploration.domain.revisit.StampResult;
import com.kobi.territory.exploration.domain.territory.CheckInContext;
import com.kobi.territory.exploration.domain.territory.RegionSnapshot;
import com.kobi.territory.exploration.domain.territory.Territory;
import com.kobi.territory.exploration.domain.territory.TerritoryRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재방문 도장 유스케이스(9단계) — "잠그고 → 불러와서 → 도메인에 시키고 → 저장 → outbox". 판정(칠했는지·해·연도당 한 번·하루 상한)은
 * StampBook·CheckInContext 가 한다.
 * <p>
 * 동시성: 하루 상한을 개인 지도 체크인과 함께 쓰므로 체크인과 같은 잠금(개인 지도 S → 개인 territory X → 탐험가 S, MemberTerritoryLock)을
 * 잡은 뒤 오늘 체크인 수·도장을 읽는다 — 동시에 체크인과 도장을 눌러도 합쳐 상한까지만. 같은 (지역, 연도)는 revisit_stamp PK 로도 막힌다.
 */
@Service
public class RevisitService implements RevisitQuery {

    static final String AGGREGATE = "StampBook";

    private final MemberTerritoryLock locks;
    private final MapAccess mapAccess;
    private final TerritoryRepository territories;
    private final StampBookRepository stampBooks;
    private final CatalogRegionDirectory regions;
    private final ExplorationSettings settings;
    private final EventOutbox outbox;
    private final RecalculationRequests recalculations;
    private final Clock clock;

    public RevisitService(MemberTerritoryLock locks, MapAccess mapAccess, TerritoryRepository territories,
                          StampBookRepository stampBooks, CatalogRegionDirectory regions, ExplorationSettings settings,
                          EventOutbox outbox, RecalculationRequests recalculations, Clock clock) {
        this.locks = locks;
        this.mapAccess = mapAccess;
        this.territories = territories;
        this.stampBooks = stampBooks;
        this.regions = regions;
        this.settings = settings;
        this.outbox = outbox;
        this.recalculations = recalculations;
        this.clock = clock;
    }

    /** POST /revisits/{code} — 도장을 받는다. 받을 수 없으면 이유에 맞는 오류(아직 안 칠함 422 REVISIT_NOT_PAINTED·같은 해 422 REVISIT_SAME_YEAR·
     * 이미 받음 409 REVISIT_ALREADY_STAMPED·하루 상한 422 DAILY_CAP_EXCEEDED). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StampResult stamp(ExplorerId explorerId, RegionCode code) {
        MapMembership personal = locks.lockAsMember(explorerId, MapSelector.of((String) null));
        RegionSnapshot region = regions.require(code);
        Territory territory = territories.load(personal.map().id());
        CheckInContext ctx = contextOf(personal);
        StampBook stampBook = stampBooks.load(explorerId);

        StampResult result = stampBook.stamp(code, territories.firstVisibleVisitAt(explorerId, code),
            territory.checkInsOn(explorerId, ctx), ctx);
        stampBooks.save(stampBook);

        RevisitStamp stamp = result.stamp();
        outbox.append(AGGREGATE, explorerId.value(), new RevisitStamped(explorerId.value(), code.value(), region.provinceCode(),
            stamp.year(), result.firstYear(), result.stampCount(), stamp.stampedAt()));
        return result;
    }

    /** GET /revisits/{code} — 지금 도장을 받을 수 있는지와 이유(버튼 안내). 잠그지 않는다(안내용 — 실제 판정은 POST 가 잠근 뒤 다시 한다). */
    @Transactional(readOnly = true)
    public StampEligibility status(ExplorerId explorerId, RegionCode code) {
        MapMembership personal = mapAccess.resolve(explorerId, MapSelector.of((String) null));
        regions.require(code);
        Territory territory = territories.load(personal.map().id());
        CheckInContext ctx = contextOf(personal);
        return stampBooks.load(explorerId).judge(code, territories.firstVisibleVisitAt(explorerId, code),
            territory.checkInsOn(explorerId, ctx), ctx);
    }

    /** GET /revisits — 내 도장첩. 탐험가가 없으면 404 EXPLORER_NOT_FOUND. */
    @Transactional(readOnly = true)
    public StampBook view(ExplorerId explorerId) {
        mapAccess.requireExplorer(explorerId);
        return stampBooks.load(explorerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevisitStampView> stampsOf(String explorerId) {
        return stampBooks.load(ExplorerId.of(explorerId)).stamps().newestFirst().reversed().stream()
            .map(stamp -> new RevisitStampView(stamp.region().value(), stamp.year(), stamp.stampedAt())).toList();
    }

    /**
     * 계정 병합(구독자 exploration.stamp-book): 익명 탐험가의 도장을 계정 탐험가 도장첩으로 합친다(같은 지역·연도는 하나). 도장 커맨드와
     * 같은 잠금(계정 탐험가 개인 territory)을 잡는다. 옮겨 온 도장의 보상(XP·뱃지·색 변형)은 재계산 예약으로 맞춘다 — 다시 소식을 내지
     * 않는다(이미 지난 일). 멱등.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onExplorerMerged(ExplorerMerged event) {
        ExplorerId into = ExplorerId.of(event.intoExplorerId());
        territories.lockPersonal(into);
        StampBook stampBook = stampBooks.load(into);
        List<RevisitStamp> adopted = stampBook.absorb(stampBooks.load(ExplorerId.of(event.fromExplorerId())));
        stampBooks.save(stampBook);
        if (!adopted.isEmpty()) recalculations.request(into.value(), "revisit-stamps-merged", event.mergedAt());
    }

    private CheckInContext contextOf(MapMembership personal) {
        return new CheckInContext(personal.map().checkInPolicy(settings.onboardingGrace()), personal.member().joinedAt(),
            clock.instant(), clock.getZone());
    }
}
