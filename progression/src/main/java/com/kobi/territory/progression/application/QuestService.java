package com.kobi.territory.progression.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.progression.api.event.QuestCompleted;
import com.kobi.territory.progression.domain.ExplorerProgressRepository;
import com.kobi.territory.progression.domain.ProgressVisit;
import com.kobi.territory.progression.domain.QuestBoard;
import com.kobi.territory.progression.domain.QuestBoardRepository;
import com.kobi.territory.progression.domain.QuestFact;
import com.kobi.territory.progression.domain.QuestPeriod;
import com.kobi.territory.progression.domain.QuestReward;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 퀘스트(QuestBoard) 유스케이스. 셀지·받을 수 있는지는 QuestBoard·QuestRules·QuestTally 가 판단한다.
 * "처음 가는 시·도"(mprov)는 explorer_region(탐험가 단위 지역 기록)으로 판단한다(QA P3-3) — 읽기만 한다.
 * 체크인 하나가 같은 탐험가의 월간·상시 보드 둘을 고친다(같은 구독자·같은 탐험가 — 트랜잭션당 애그리거트 하나 규칙의 예외).
 */
@Service
public class QuestService {

    static final String AGGREGATE = "QuestBoard";

    private final QuestBoardRepository boards;
    private final ExplorerProgressRepository progresses;
    private final ProgressionCatalog catalog;
    private final TerritoryQuery territories;
    private final EventOutbox outbox;
    private final Clock clock;

    public QuestService(QuestBoardRepository boards, ExplorerProgressRepository progresses, ProgressionCatalog catalog,
                        TerritoryQuery territories, EventOutbox outbox, Clock clock) {
        this.boards = boards;
        this.progresses = progresses;
        this.catalog = catalog;
        this.territories = territories;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** 체크인 → 처리 시각이 속한 달의 월간 보드 + 상시 보드에 센다(지난 달 보드면 도메인이 무시). */
    @Transactional
    public void onRegionVisited(RegionVisited event) {
        ExplorerId explorerId = ExplorerId.of(event.explorerId());
        ProgressVisit visit = ProgressService.visitOf(event);
        QuestFact fact = QuestFact.of(visit, catalog.sets(), progresses.exploredRegions(explorerId));
        YearMonth now = catalog.currentMonth();
        apply(boards.load(explorerId, QuestPeriod.monthOf(event.visitedAt(), clock.getZone())), fact, now);
        apply(boards.load(explorerId, QuestPeriod.ALL), fact, now);
    }

    /** GET /quests — 이번 달 보드 + 상시 보드. */
    @Transactional(readOnly = true)
    public Boards view(ExplorerId explorerId) {
        territories.personalMapId(explorerId.value());
        YearMonth now = catalog.currentMonth();
        return new Boards(now, boards.load(explorerId, QuestPeriod.of(now)), boards.load(explorerId, QuestPeriod.ALL));
    }

    /** POST /quests/{questId}/claim — 보상은 QuestCompleted 로 진행에 전달(비동기). */
    @Transactional
    public QuestReward claim(ExplorerId explorerId, String questId) {
        territories.personalMapId(explorerId.value());
        YearMonth now = catalog.currentMonth();
        QuestBoard board = boards.load(explorerId, catalog.quests().require(questId).periodAt(now));
        QuestReward reward = board.claim(questId, catalog.quests(), clock.instant(), now);
        boards.save(board);
        outbox.append(AGGREGATE, explorerId.value(), new QuestCompleted(explorerId.value(), reward.period().value(),
            reward.questId(), reward.xp(), reward.claimedAt()));
        return reward;
    }

    private void apply(QuestBoard board, QuestFact fact, YearMonth now) {
        board.applyVisit(fact, catalog.quests(), now);
        boards.save(board);
    }

    public record Boards(YearMonth month, QuestBoard monthly, QuestBoard always) {}
}
