package com.kobi.territory.progression.application;

import com.kobi.territory.common.event.EventBacklog;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.query.RevisitQuery;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.exploration.api.query.WishlistQuery;
import com.kobi.territory.progression.domain.progress.StampFact;
import com.kobi.territory.progression.domain.progress.WishFact;
import com.kobi.territory.progression.domain.collectionbook.CollectionBook;
import com.kobi.territory.progression.domain.collectionbook.CollectionBookRepository;
import com.kobi.territory.progression.domain.progress.ExplorerProgress;
import com.kobi.territory.progression.domain.progress.ExplorerProgressRepository;
import com.kobi.territory.progression.domain.replay.ProgressionReplay;
import com.kobi.territory.progression.domain.quest.QuestBoard;
import com.kobi.territory.progression.domain.quest.QuestBoardRepository;
import com.kobi.territory.progression.domain.replay.ReplayVisit;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재계산 배치(일관성 원칙 3) — 정의 변경·버그 복구용. Territory(탐험 Query 의 visitHistory)로부터 진행·도감·퀘스트를
 * 다시 만든다(현재 방문으로 정해지는 값은 다시 만들고, 취소 비대칭으로 남은 보상·완성 기록은 지우지 않는 "덧붙이기" +
 * 잃은 세트·퀘스트 보상 복구). 재생 규칙은 도메인 서비스 ProgressionReplay 가 갖고, 여기는 불러오기·저장 순서만 둔다.
 * 진입점: local 전용 POST /dev/recalculate, 운영은 기동 인자 --territory.progression.recalculate-on-startup=true
 * (릴레이가 outbox 를 비운 뒤 실행).
 * <p>
 * 보류(구조 QA S3-3): 탐험가(와 그가 속한 지도)에 아직 릴레이가 전달하지 않은 outbox 이벤트가 남아 있으면 그 탐험가는 건너뛰고
 * 보고서의 deferred 에 넣는다 — 재계산이 릴레이보다 앞선 영토를 읽으면 "잠깐 있었던 완성"(체크인 → 취소 사이의 테마 완성)을
 * 놓쳐 이벤트 누적과 달라지기 때문이다. 보류된 탐험가는 릴레이가 비운 뒤 다시 돌리면 된다. 운영은 트래픽 적은 시간에 돌린다
 * (재계산이 진행 루트를 잠그는 동안 사용자의 칭호 선택은 409 로 실패할 수 있다 — S3-2).
 */
@Service
public class RecalculateService {

    private static final Logger log = LoggerFactory.getLogger(RecalculateService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final TerritoryQuery territories;
    private final ExplorerProgressRepository progresses;
    private final CollectionBookRepository collectionBooks;
    private final QuestBoardRepository boards;
    private final ProgressionCatalog catalog;
    private final Clock clock;
    private final EventBacklog backlog;
    private final TransactionTemplate perExplorerTx;
    private final ProgressService progressService;
    private final RevisitQuery revisits;
    private final WishlistQuery wishlists;

    public RecalculateService(TerritoryQuery territories, ProgressService progressService, ExplorerProgressRepository progresses,
                              CollectionBookRepository collectionBooks, QuestBoardRepository boards,
                              ProgressionCatalog catalog, Clock clock, EventBacklog backlog,
                              PlatformTransactionManager transactionManager, RevisitQuery revisits, WishlistQuery wishlists) {
        this.revisits = revisits;
        this.wishlists = wishlists;
        this.backlog = backlog;
        this.progressService = progressService;
        this.territories = territories;
        this.progresses = progresses;
        this.collectionBooks = collectionBooks;
        this.boards = boards;
        this.catalog = catalog;
        this.clock = clock;
        this.perExplorerTx = new TransactionTemplate(transactionManager);
    }

    /**
     * 한 탐험가(와 그가 속한 지도의 도감)를 다시 만든다. 진행 루트 행을 먼저 배타 잠금하고 읽는다(findLocked) — 진행 이벤트
     * 처리도 같은 잠금으로 시작하므로 직렬화된다. 도감·퀘스트 행은 version 검사로 지킨다(재계산이 읽은 뒤 바뀌면 충돌).
     * 잠금 경합·교착·version 충돌·신규 행 동시 생성(PK 충돌)으로 이 트랜잭션이 실패하면 몇 번 다시 시도한다
     * (트랜잭션 경계·재시도는 application 몫, QA S-1·S2-1·S2-4).
     */
    public ExplorerProgress recalculate(ExplorerId explorerId) {
        return withRetry(explorerId, false).orElseThrow();
    }

    /**
     * 미전달 이벤트가 없을 때만 재계산한다(S3-3). 판정은 진행 루트를 잠근 <b>뒤</b> 같은 트랜잭션 안에서 한다(QA P3-5 — 판정과
     * 영토 읽기 사이에 끼어드는 체크인을 막는다: 그 체크인의 이벤트도 진행 루트 잠금을 기다린다).
     * @return 재계산했으면 true, 보류했으면 false
     */
    public boolean recalculateIfSettled(ExplorerId explorerId) {
        return withRetry(explorerId, true).isPresent();
    }

    private Optional<ExplorerProgress> withRetry(ExplorerId explorerId, boolean onlyIfSettled) {
        for (int attempt = 1; ; attempt++) {
            try {
                return perExplorerTx.execute(status -> recalculateLocked(explorerId, onlyIfSettled));
            } catch (ConcurrencyFailureException | DataIntegrityViolationException exception) {
                if (attempt >= MAX_ATTEMPTS) throw exception;
                log.info("재계산 {} 동시성 충돌 {}회째 — 다시 시도: {}", explorerId, attempt, exception.toString());
            }
        }
    }

    private Optional<ExplorerProgress> recalculateLocked(ExplorerId explorerId, boolean onlyIfSettled) {
        ExplorerProgress current = progresses.findLocked(explorerId)
            .orElseGet(() -> ExplorerProgress.start(explorerId, catalog.policy(), clock.instant()));
        if (onlyIfSettled && !settled(explorerId)) return Optional.empty();
        Map<String, List<ReplayVisit>> histories = new LinkedHashMap<>();
        territories.mapIdsOf(explorerId.value()).forEach(mapId -> histories.put(mapId,
            territories.visitHistory(mapId).stream().map(this::replayVisit).toList()));
        Map<String, CollectionBook> existingBooks = new LinkedHashMap<>();
        histories.keySet().forEach(mapId -> existingBooks.put(mapId, collectionBooks.load(mapId)));
        List<QuestBoard> explorerBoards = boards.loadAll(explorerId);
        YearMonth now = catalog.currentMonth();

        List<StampFact> stamps = revisits.stampsOf(explorerId.value()).stream()
            .map(stamp -> new StampFact(RegionCode.of(stamp.regionCode()), stamp.year(), stamp.stampedAt())).toList();
        List<WishFact> wishes = wishlists.fulfilledOf(explorerId.value()).stream()
            .map(wish -> new WishFact(RegionCode.of(wish.regionCode()), wish.fulfilledAt())).toList();

        ProgressionReplay.Result result = ProgressionReplay.replay(explorerId, current, histories, existingBooks,
            explorerBoards, catalog.policy(), catalog.themes(), catalog.seasonCalendar(), catalog.questRules(), now,
            clock.instant(), stamps, wishes);

        // 재계산은 통째로 바꾸는 경로 — 저장소에 replace 를 명시해 호출한다(어댑터가 의도를 추측하지 않게)
        progresses.replace(result.progress());
        result.collectionBooks().forEach(collectionBooks::replace);
        boards.replace(result.monthly());
        boards.replace(result.always());
        return Optional.of(result.progress());
    }

    /**
     * 모든 탐험가. 탐험가마다 트랜잭션·재시도를 나누고 실패를 격리한다 — 한 명이 재시도를 다 써도 나머지는 계속한다(QA S2-3).
     * 미전달 이벤트가 남은 탐험가는 보류(deferred)한다(S3-3).
     */
    public RecalculationReport recalculateAll() {
        List<String> failed = new ArrayList<>();
        List<String> deferred = new ArrayList<>();
        List<String> explorerIds = territories.explorerIds();
        explorerIds.forEach(explorerId -> {
            try {
                if (!recalculateIfSettled(ExplorerId.of(explorerId))) deferred.add(explorerId);
            } catch (RuntimeException exception) {
                failed.add(explorerId);
                log.error("진행 재계산 실패(다음 탐험가로 계속): {} — {}", explorerId, exception.toString());
            }
        });
        RecalculationReport report = new RecalculationReport(explorerIds.size() - failed.size() - deferred.size(), failed,
            deferred);
        log.info("진행 재계산 완료: 성공 {}명, 실패 {}명 {}, 보류 {}명 {}", report.recalculated(), failed.size(), failed,
            deferred.size(), deferred);
        return report;
    }

    /**
     * 탐험가와 그가 속한 지도의 outbox 이벤트 중 진행 구독자(progression.*)·탐험 영토 구독자(exploration.territory, P3-R3-1)에게
     * 아직 전달되지 않은 것이 없는지 — 그 밖의 구독자(꾸미기 등)가 멈춰 있어도 재계산을 막지 않는다(QA P3-6).
     */
    private boolean settled(ExplorerId explorerId) {
        List<String> aggregateIds = new ArrayList<>(territories.mapIdsOf(explorerId.value()));
        aggregateIds.add(explorerId.value());
        return !backlog.hasUndelivered(aggregateIds, HOLD_SUBSCRIBERS);
    }

    /** 진행 컨텍스트 구독자 id 접두사(ProgressionSubscriptions). */
    static final String SUBSCRIBER_PREFIX = "progression.";

    /**
     * 보류 판정 구독자: 진행 구독자 + 탐험 영토 구독자(exploration.territory — 탈퇴 숨김·재가입 복구·병합 흡수가 아직 영토에 반영되지
     * 않았으면 재계산이 그 지도를 덜 읽어 회수해 버린다, P3-R3-1). 9단계: 도장첩·가고 싶은 곳 구독자(병합으로 옮겨 올 도장·핀이 아직이면
     * 복구 규칙이 그 보상을 놓친다).
     */
    static final List<String> HOLD_SUBSCRIBERS = List.of(SUBSCRIBER_PREFIX, "exploration.territory", "exploration.expedition-map",
        "exploration.stamp-book", "exploration.wishlist");

    /**
     * @param failedExplorerIds   재시도를 다 쓰고도 실패한 탐험가(다시 돌리면 된다)
     * @param deferredExplorerIds 미전달 이벤트가 남아 건너뛴 탐험가(릴레이가 비운 뒤 다시 돌리면 된다 — S3-3)
     */
    public record RecalculationReport(int recalculated, List<String> failedExplorerIds, List<String> deferredExplorerIds) {
        public RecalculationReport {
            failedExplorerIds = List.copyOf(failedExplorerIds);
            deferredExplorerIds = List.copyOf(deferredExplorerIds);
        }

    }

    private ReplayVisit replayVisit(RegionVisited event) {
        List<ExplorerId> members = event.memberIds() == null ? List.of() : event.memberIds().stream().map(ExplorerId::of).toList();
        return new ReplayVisit(ExplorerId.of(event.explorerId()), progressService.visitWithMystery(event), members);
    }
}
