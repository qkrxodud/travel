package com.kobi.territory.exploration.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.event.RecalculationRequests;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.ExplorerMerged;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.WishFulfilled;
import com.kobi.territory.exploration.api.query.WishlistQuery;
import com.kobi.territory.exploration.domain.territory.TerritoryRepository;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.wishlist.WishFulfillment;
import com.kobi.territory.exploration.domain.wishlist.WishPin;
import com.kobi.territory.exploration.domain.wishlist.Wishlist;
import com.kobi.territory.exploration.domain.wishlist.WishlistRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가고 싶은 곳 유스케이스(9단계) — "잠그고 → 불러와서 → 도메인에 시키고 → 저장 → outbox". 판정(칠한 곳 불가·상한·다녀옴 한 번)은
 * Wishlist 가 한다. 루트 행(wishlist)이 핀 꽂기와 다녀옴 처리를 직렬화한다 — 루트가 없으면 따로 커밋되는 트랜잭션에서 먼저 만든다.
 * 다녀옴은 체크인 소식(RegionVisited, 구독자 exploration.wishlist)으로 판정한다(멱등 — 이미 다녀온 핀은 그대로).
 */
@Service
public class WishlistService implements WishlistQuery {

    static final String AGGREGATE = "Wishlist";

    private final WishlistRepository wishlists;
    private final TerritoryRepository territories;
    private final CatalogRegionDirectory regions;
    private final MapAccess mapAccess;
    private final WishlistSettings settings;
    private final EventOutbox outbox;
    private final RecalculationRequests recalculations;
    private final Clock clock;
    private final TransactionTemplate separateTx;

    public WishlistService(WishlistRepository wishlists, TerritoryRepository territories, CatalogRegionDirectory regions,
                           MapAccess mapAccess, WishlistSettings settings, EventOutbox outbox,
                           RecalculationRequests recalculations, Clock clock, PlatformTransactionManager transactionManager) {
        this.wishlists = wishlists;
        this.territories = territories;
        this.regions = regions;
        this.mapAccess = mapAccess;
        this.settings = settings;
        this.outbox = outbox;
        this.recalculations = recalculations;
        this.clock = clock;
        this.separateTx = new TransactionTemplate(transactionManager);
        this.separateTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.separateTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * PUT /wishlist/{code} — 핀을 꽂는다(이미 꽂혀 있으면 그대로). 칠한 지역 409 WISH_ALREADY_VISITED, 상한 422 WISHLIST_FULL.
     * <p>
     * 잠금·격리 규칙: 상한("지금 몇 개인가")으로 판단하는 쓰기라 READ_COMMITTED + 루트 행 잠금이 이 트랜잭션의 첫 조회다(탐험가 확인은 잠금 뒤
     * 다시) — 잠금 전 일반 조회가 MySQL REPEATABLE READ 스냅숏을 고정하면 동시에 들어온 핀을 못 봐 상한이 뚫린다(9단계 QA P1-1).
     * 루트가 없으면 먼저 따로 커밋되는 트랜잭션에서 만든다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Wishlist pin(ExplorerId explorerId, RegionCode code) {
        regions.require(code);
        Instant now = clock.instant();
        ensureRoot(explorerId, now);
        Wishlist wishlist = wishlists.findLocked(explorerId)
            .orElseThrow(ExplorationError.EXPLORER_NOT_FOUND::exception);  // 루트 잠금 — 첫 조회
        mapAccess.requireExplorer(explorerId);                             // 잠금 뒤 다시 확인
        wishlist.pin(code, territories.hasVisibleVisit(explorerId, code), now, settings.policy());
        wishlists.save(wishlist);
        return wishlist;
    }

    /** DELETE /wishlist/{code} — 핀을 뺀다(없으면 그대로). 같은 규칙(READ_COMMITTED, 잠금이 첫 조회). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void unpin(ExplorerId explorerId, RegionCode code) {
        Optional<Wishlist> locked = wishlists.findLocked(explorerId);
        mapAccess.requireExplorer(explorerId);
        locked.ifPresent(wishlist -> {
            wishlist.unpin(code);
            wishlists.save(wishlist);
        });
    }

    /** GET /wishlist — 내 가고 싶은 곳(비공개). 탐험가가 없으면 404 EXPLORER_NOT_FOUND. */
    @Transactional(readOnly = true)
    public Wishlist view(ExplorerId explorerId) {
        mapAccess.requireExplorer(explorerId);
        return wishlists.load(explorerId);
    }

    /**
     * 체크인(구독자 exploration.wishlist) — 칠한 사람의 대기 핀이 그 체크인으로 이루어지면 다녀옴 → WishFulfilled. 루트 잠금이 첫 조회이고,
     * 잠금 뒤 지금 칠해져 있는지 다시 본다(핀 꽂기와 거의 동시인 체크인도 다녀옴으로 — QA P3-2). 멱등.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onRegionVisited(RegionVisited event) {
        ExplorerId explorerId = ExplorerId.of(event.explorerId());
        RegionCode region = RegionCode.of(event.regionCode());
        // 루트가 없으면 핀을 꽂은 적이 없다 — 할 일 없음
        wishlists.findLocked(explorerId).ifPresent(wishlist ->
            wishlist.fulfill(region, event.visitedAt(), territories.hasVisibleVisit(explorerId, region)).ifPresent(fulfillment -> {
                wishlists.save(wishlist);
                publish(explorerId, List.of(fulfillment));
            }));
    }

    /**
     * 계정 병합(구독자 exploration.wishlist): 익명 탐험가의 핀을 계정 탐험가로 합치고, 합친 뒤 대기 핀 중 이미 칠해진 지역(계정·익명 어느 쪽 방문이든
     * — 흡수 처리와 순서가 독립이라 둘 다 본다)은 다녀옴으로 바꾼다(리더 결정 Q1, XP 는 지역당 한 번 그대로). 익명 쪽에서 이미 다녀온 핀의 보상은
     * 재계산 예약으로 맞춘다. 같은 잠금 규칙(READ_COMMITTED, 계정 루트 잠금이 첫 조회). 멱등.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onExplorerMerged(ExplorerMerged event) {
        ExplorerId into = ExplorerId.of(event.intoExplorerId());
        ExplorerId from = ExplorerId.of(event.fromExplorerId());
        ensureRoot(into, event.mergedAt());
        Wishlist wishlist = wishlists.findLocked(into).orElseThrow(ExplorationError.EXPLORER_NOT_FOUND::exception);
        List<WishPin> newlyFulfilled = wishlist.absorb(wishlists.load(from));
        List<RegionCode> pending = wishlist.pins().pendingRegions();
        Set<RegionCode> painted = new HashSet<>(territories.visibleRegionsAmong(into, pending));
        painted.addAll(territories.visibleRegionsAmong(from, pending));
        List<WishFulfillment> visitedNow = wishlist.fulfillPainted(painted, event.mergedAt());
        wishlists.save(wishlist);
        publish(into, visitedNow);
        if (!newlyFulfilled.isEmpty()) recalculations.request(into.value(), "wishes-merged", event.mergedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FulfilledWishView> fulfilledOf(String explorerId) {
        return wishlists.load(ExplorerId.of(explorerId)).pins().fulfilled().stream()
            .map(pin -> new FulfilledWishView(pin.region().value(), pin.fulfilledAt())).toList();
    }

    public WishlistSettings settings() {
        return settings;
    }

    /**
     * 루트 행 보장 — 따로 커밋되는 READ_COMMITTED 트랜잭션에서(동시에 만들 때의 유일성 위반·교착이 본 트랜잭션을 오염시키지 않게). 동시에 만든
     * 쪽이 이기면 이미 목표 상태라 그 실패는 흡수한다 — 이어지는 루트 잠금이 남이 만든 행을 기다려 읽는다(QA P3-1). 탐험가가 없으면 만들지 않는다.
     */
    private void ensureRoot(ExplorerId explorerId, Instant at) {
        try {
            separateTx.executeWithoutResult(status -> {
                mapAccess.requireExplorer(explorerId);
                wishlists.ensure(explorerId, at);
            });
        } catch (DataIntegrityViolationException | ConcurrencyFailureException | TransactionException alreadyBeingCreated) {
            // 다른 요청이 같은 루트를 먼저 만들었다(유일성 위반·교착) — 목표 상태
        }
    }

    private void publish(ExplorerId explorerId, List<WishFulfillment> fulfillments) {
        fulfillments.forEach(fulfillment -> outbox.append(AGGREGATE, explorerId.value(), new WishFulfilled(explorerId.value(),
            fulfillment.region().value(), fulfillment.pinnedAt(), fulfillment.fulfilledAt())));
    }
}
