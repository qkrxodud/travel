package com.kobi.territory.progression.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
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
 * 진입점: local 전용 POST /dev/recalculate, 운영은 기동 인자 --territory.progression.recalculate-on-startup=true.
 * 릴레이와 동시에 돌리는 것은 가정하지 않는다(운영 배치는 트래픽이 없을 때).
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
    private final TransactionTemplate perExplorerTx;

    public RecalculateService(TerritoryQuery territories, ExplorerProgressRepository progresses,
                              CollectionBookRepository collectionBooks, QuestBoardRepository boards,
                              ProgressionCatalog catalog, Clock clock, PlatformTransactionManager transactionManager) {
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
        for (int attempt = 1; ; attempt++) {
            try {
                return perExplorerTx.execute(status -> recalculateLocked(explorerId));
            } catch (ConcurrencyFailureException | DataIntegrityViolationException exception) {
                if (attempt >= MAX_ATTEMPTS) throw exception;
                log.info("재계산 {} 동시성 충돌 {}회째 — 다시 시도: {}", explorerId, attempt, exception.toString());
            }
        }
    }

    private ExplorerProgress recalculateLocked(ExplorerId explorerId) {
        ExplorerProgress current = progresses.findLocked(explorerId)
            .orElseGet(() -> ExplorerProgress.start(explorerId, catalog.policy(), clock.instant()));
        Map<String, List<ReplayVisit>> histories = new LinkedHashMap<>();
        territories.mapIdsOf(explorerId.value()).forEach(mapId -> histories.put(mapId,
            territories.visitHistory(mapId).stream().map(RecalculateService::replayVisit).toList()));
        Map<String, CollectionBook> existingBooks = new LinkedHashMap<>();
        histories.keySet().forEach(mapId -> existingBooks.put(mapId, collectionBooks.load(mapId)));
        List<QuestBoard> explorerBoards = boards.loadAll(explorerId);
        YearMonth now = catalog.currentMonth();

        ProgressionReplay.Result result = ProgressionReplay.replay(explorerId, current, histories, existingBooks,
            explorerBoards, catalog.policy(), catalog.themes(), catalog.questRules(), now, clock.instant());

        // 재계산은 통째로 바꾸는 경로 — 저장소에 replace 를 명시해 호출한다(어댑터가 의도를 추측하지 않게)
        progresses.replace(result.progress());
        result.collectionBooks().forEach(collectionBooks::replace);
        boards.replace(result.monthly());
        boards.replace(result.always());
        return result.progress();
    }

    /**
     * 모든 탐험가. 탐험가마다 트랜잭션·재시도를 나누고 실패를 격리한다 — 한 명이 재시도를 다 써도 나머지는 계속한다(QA S2-3).
     */
    public RecalculationReport recalculateAll() {
        List<String> failed = new ArrayList<>();
        List<String> explorerIds = territories.explorerIds();
        explorerIds.forEach(explorerId -> {
            try {
                recalculate(ExplorerId.of(explorerId));
            } catch (RuntimeException exception) {
                failed.add(explorerId);
                log.error("진행 재계산 실패(다음 탐험가로 계속): {} — {}", explorerId, exception.toString());
            }
        });
        RecalculationReport report = new RecalculationReport(explorerIds.size() - failed.size(), failed);
        log.info("진행 재계산 완료: 성공 {}명, 실패 {}명 {}", report.recalculated(), failed.size(), failed);
        return report;
    }

    /** @param failedExplorerIds 재시도를 다 쓰고도 실패한 탐험가(다시 돌리면 된다) */
    public record RecalculationReport(int recalculated, List<String> failedExplorerIds) {
        public RecalculationReport {
            failedExplorerIds = List.copyOf(failedExplorerIds);
        }
    }

    private static ReplayVisit replayVisit(RegionVisited event) {
        return new ReplayVisit(ExplorerId.of(event.explorerId()), ProgressService.visitOf(event));
    }
}
