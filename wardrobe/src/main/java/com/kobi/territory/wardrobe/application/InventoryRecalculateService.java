package com.kobi.territory.wardrobe.application;

import com.kobi.territory.common.event.EventBacklog;
import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.progression.api.query.CollectionBookQuery;
import com.kobi.territory.progression.api.query.CompletedSetView;
import com.kobi.territory.wardrobe.api.event.SceneChanged;
import com.kobi.territory.wardrobe.domain.inventory.CheckInGrant;
import com.kobi.territory.wardrobe.domain.inventory.Inventory;
import com.kobi.territory.wardrobe.domain.inventory.InventoryReplay;
import com.kobi.territory.wardrobe.domain.inventory.InventoryRepository;
import com.kobi.territory.wardrobe.domain.inventory.ReplayVisit;
import com.kobi.territory.wardrobe.domain.scene.Holdings;
import com.kobi.territory.wardrobe.domain.scene.Scene;
import com.kobi.territory.wardrobe.domain.scene.SceneRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 인벤토리 재계산 배치(일관성 원칙 3, QA P2-1) — progression RecalculateService 와 같은 틀:
 * 루트 행 선잠금(findLocked) → 재생(InventoryReplay) → replace(자식 행 지우고 다시 넣기, 루트 version 강제 증가),
 * 미전달 이벤트가 남은 탐험가는 보류(deferred), 탐험가별 트랜잭션·재시도·실패 격리.
 * 입력: 탐험 Query 의 방문 이력(지금 멤버인 모든 지도), 진행 Query 의 완성 테마, 카탈로그 아이템 정의·지급 규칙.
 * 장면은 착용 선택을 유지하고 가방에 없게 된 아이템만 벗긴다(바뀌면 SceneChanged). 아이템 이벤트(ItemGranted·ItemRevoked)는
 * 내지 않는다 — 재계산으로 돌아온 아이템을 자동 착용하지 않게.
 * 진입점: local POST /dev/recalculate(진행 다음에 함께 실행), 운영은 기동 인자 recalculate-on-startup(진행 다음).
 */
@Service
public class InventoryRecalculateService {

    private static final Logger log = LoggerFactory.getLogger(InventoryRecalculateService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final String SUBSCRIBER_PREFIX = "wardrobe.";
    /** 보류 판정 구독자: 꾸미기 구독자 + 탐험 영토 구독자(숨김·복구·병합 흡수 반영 전 재계산 방지, P3-R3-1). */
    private static final List<String> HOLD_SUBSCRIBERS = List.of(SUBSCRIBER_PREFIX, "exploration.territory",
        "exploration.expedition-map");

    private final InventoryRepository inventories;
    private final SceneRepository scenes;
    private final TerritoryQuery territories;
    private final CollectionBookQuery collectionBooks;
    private final WardrobeCatalog catalog;
    private final EventBacklog backlog;
    private final EventOutbox outbox;
    private final Clock clock;
    private final TransactionTemplate perExplorerTx;

    public InventoryRecalculateService(InventoryRepository inventories, SceneRepository scenes, TerritoryQuery territories,
                                       CollectionBookQuery collectionBooks, WardrobeCatalog catalog, EventBacklog backlog,
                                       EventOutbox outbox, Clock clock, PlatformTransactionManager transactionManager) {
        this.inventories = inventories;
        this.scenes = scenes;
        this.territories = territories;
        this.collectionBooks = collectionBooks;
        this.catalog = catalog;
        this.backlog = backlog;
        this.outbox = outbox;
        this.clock = clock;
        this.perExplorerTx = new TransactionTemplate(transactionManager);
    }

    /** 한 탐험가(보류 없이). 잠금 경합·교착·version 충돌·신규 행 동시 생성으로 실패하면 몇 번 다시 시도한다. */
    public Inventory recalculate(ExplorerId explorerId) {
        return recalculate(explorerId, false).orElseThrow();
    }

    /**
     * 미전달 이벤트가 없을 때만 재계산한다(S3-3과 같은 규칙). 판정은 루트를 잠근 뒤 같은 트랜잭션에서 한다(QA P3-R2-3 — 진행과 같은
     * 방식: 판정과 이력 읽기 사이에 끼어든 이벤트는 잠금에 막혀 기다린다). 기준은 꾸미기 구독자(wardrobe.*)에게 아직 전달되지 않은
     * 이벤트 — 진행 쪽은 progression.* 기준이다(각자 자기 구독자의 미전달만 본다). @return 재계산했으면 true, 보류했으면 false
     */
    public boolean recalculateIfSettled(ExplorerId explorerId) {
        return recalculate(explorerId, true).isPresent();
    }

    private Optional<Inventory> recalculate(ExplorerId explorerId, boolean onlyIfSettled) {
        for (int attempt = 1; ; attempt++) {
            try {
                return perExplorerTx.execute(status -> recalculateLocked(explorerId, onlyIfSettled));
            } catch (ConcurrencyFailureException | DataIntegrityViolationException exception) {
                if (attempt >= MAX_ATTEMPTS) throw exception;
                log.info("인벤토리 재계산 {} 동시성 충돌 {}회째 — 다시 시도: {}", explorerId, attempt, exception.toString());
            }
        }
    }

    private Optional<Inventory> recalculateLocked(ExplorerId explorerId, boolean onlyIfSettled) {
        Inventory current = inventories.findLocked(explorerId).orElseGet(() -> Inventory.empty(explorerId, clock.instant()));
        Set<String> mapIds = new LinkedHashSet<>(territories.mapIdsOf(explorerId.value()));
        if (onlyIfSettled && !settled(explorerId, mapIds)) return Optional.empty();
        List<ReplayVisit> visits = new ArrayList<>();
        mapIds.forEach(mapId -> territories.visitHistory(mapId).forEach(event -> visits.add(replayVisit(event))));
        List<CompletedSetView> completedSets = new ArrayList<>();
        mapIds.forEach(mapId -> completedSets.addAll(collectionBooks.completedSets(mapId)));

        Inventory rebuilt = InventoryReplay.replay(current, mapIds, visits, catalog.grantedByThemeCompletions(completedSets),
            clock.instant());

        inventories.replace(rebuilt);
        Scene scene = scenes.find(explorerId).orElseGet(() -> Scene.blank(explorerId, clock.instant()));
        scene.keepOnly(Holdings.of(rebuilt.ownedItems().itemIds()), clock.instant()).ifPresent(update -> {
            scenes.save(scene);
            outbox.append(SceneService.AGGREGATE, explorerId.value(), new SceneChanged(explorerId.value(), update.at()));
        });
        return Optional.of(rebuilt);
    }

    /** 탐험가(인벤토리·장면 이벤트)와 그가 속한 지도에 꾸미기 구독자·탐험 영토 구독자가 아직 받지 못한 이벤트가 없는지(P3-R3-1). */
    private boolean settled(ExplorerId explorerId, Set<String> mapIds) {
        List<String> aggregateIds = new ArrayList<>(mapIds);
        aggregateIds.add(explorerId.value());
        return !backlog.hasUndelivered(aggregateIds, HOLD_SUBSCRIBERS);
    }

    /** 모든 탐험가 — 탐험가마다 트랜잭션·재시도를 나누고 실패를 격리한다. */
    public RecalculationReport recalculateAll() {
        List<String> failed = new ArrayList<>();
        List<String> deferred = new ArrayList<>();
        List<String> explorerIds = territories.explorerIds();
        explorerIds.forEach(explorerId -> {
            try {
                if (!recalculateIfSettled(ExplorerId.of(explorerId))) deferred.add(explorerId);
            } catch (RuntimeException exception) {
                failed.add(explorerId);
                log.error("인벤토리 재계산 실패(다음 탐험가로 계속): {} — {}", explorerId, exception.toString());
            }
        });
        RecalculationReport report = new RecalculationReport(explorerIds.size() - failed.size() - deferred.size(), failed,
            deferred);
        log.info("인벤토리 재계산 완료: 성공 {}명, 실패 {}명 {}, 보류 {}명 {}", report.recalculated(), failed.size(), failed,
            deferred.size(), deferred);
        return report;
    }

    private ReplayVisit replayVisit(RegionVisited event) {
        return new ReplayVisit(ExplorerId.of(event.explorerId()), new CheckInGrant(event.mapId(),
            RegionCode.of(event.regionCode()), event.visitGeneration(),
            catalog.grantedByCheckIn(event.regionCode(), event.provinceCode(), event.visitedAt()), event.visitedAt()));
    }

    public record RecalculationReport(int recalculated, List<String> failedExplorerIds, List<String> deferredExplorerIds) {
        public RecalculationReport {
            failedExplorerIds = List.copyOf(failedExplorerIds);
            deferredExplorerIds = List.copyOf(deferredExplorerIds);
        }
    }
}
