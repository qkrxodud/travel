package com.kobi.territory.progression.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.progression.domain.CollectionBook;
import com.kobi.territory.progression.domain.CollectionBookRepository;
import com.kobi.territory.progression.domain.ExplorerProgress;
import com.kobi.territory.progression.domain.ExplorerProgressRepository;
import com.kobi.territory.progression.domain.ProgressionReplay;
import com.kobi.territory.progression.domain.QuestBoard;
import com.kobi.territory.progression.domain.QuestBoardRepository;
import com.kobi.territory.progression.domain.ReplayVisit;
import java.time.Clock;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
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

    /** 한 탐험가(와 그가 속한 지도의 도감)를 다시 만든다. */
    @Transactional
    public ExplorerProgress recalculate(ExplorerId explorerId) {
        Map<String, List<ReplayVisit>> histories = new LinkedHashMap<>();
        territories.mapIdsOf(explorerId.value()).forEach(mapId -> histories.put(mapId,
            territories.visitHistory(mapId).stream().map(RecalculateService::replayVisit).toList()));
        Map<String, CollectionBook> existingBooks = new LinkedHashMap<>();
        histories.keySet().forEach(mapId -> existingBooks.put(mapId, collectionBooks.load(mapId)));
        ExplorerProgress current = progresses.find(explorerId)
            .orElseGet(() -> ExplorerProgress.start(explorerId, catalog.policy(), clock.instant()));
        List<QuestBoard> explorerBoards = boards.loadAll(explorerId);
        YearMonth now = catalog.currentMonth();

        ProgressionReplay.Result result = ProgressionReplay.replay(explorerId, current, histories, existingBooks,
            explorerBoards, catalog.policy(), catalog.sets(), catalog.quests(), now, clock.instant());

        progresses.save(result.progress());
        result.collections().forEach(collectionBooks::save);
        boards.save(result.monthly());
        boards.save(result.always());
        return result.progress();
    }

    /** 모든 탐험가. 탐험가마다 트랜잭션을 나눈다(한 명 실패가 전체를 되돌리지 않게). @return 처리한 탐험가 수 */
    public int recalculateAll() {
        List<String> explorerIds = territories.explorerIds();
        explorerIds.forEach(explorerId -> perExplorerTx.executeWithoutResult(status -> recalculate(ExplorerId.of(explorerId))));
        log.info("진행 재계산 완료: 탐험가 {}명", explorerIds.size());
        return explorerIds.size();
    }

    private static ReplayVisit replayVisit(RegionVisited event) {
        return new ReplayVisit(ExplorerId.of(event.explorerId()), ProgressService.visitOf(event));
    }
}
