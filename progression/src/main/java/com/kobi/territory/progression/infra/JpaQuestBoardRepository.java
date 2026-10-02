package com.kobi.territory.progression.infra;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.domain.QuestBoard;
import com.kobi.territory.progression.domain.QuestBoardRepository;
import com.kobi.territory.progression.domain.QuestPeriod;
import com.kobi.territory.progression.domain.QuestProgress;
import com.kobi.territory.progression.domain.QuestTally;
import com.kobi.territory.progression.infra.ProgressJpaEntities.QuestRow;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * QuestBoard ↔ quest_progress(explorer_id, quest_period, quest_id). tally 는 "시·도|지역" 키의 쉼표 구분 문자열,
 * current_count 는 센 지역 키 수(조회 편의). version 낙관적 락으로 동시 보상 받기를 막는다.
 */
@Repository
class JpaQuestBoardRepository implements QuestBoardRepository {

    private final QuestRowRepository questRows;

    JpaQuestBoardRepository(QuestRowRepository questRows) {
        this.questRows = questRows;
    }

    @Override
    public QuestBoard load(ExplorerId explorerId, QuestPeriod period) {
        return toBoard(explorerId, period, questRows.findByExplorerIdAndQuestPeriod(explorerId.value(), period.value()));
    }

    @Override
    public List<QuestBoard> loadAll(ExplorerId explorerId) {
        return questRows.findByExplorerId(explorerId.value()).stream()
            .collect(Collectors.groupingBy(QuestRow::getQuestPeriod)).entrySet().stream()
            .map(byPeriod -> toBoard(explorerId, new QuestPeriod(byPeriod.getKey()), byPeriod.getValue()))
            .toList();
    }

    private static QuestBoard toBoard(ExplorerId explorerId, QuestPeriod period, List<QuestRow> rows) {
        return QuestBoard.restore(explorerId, period, rows.stream()
            .map(questRow -> new QuestProgress(questRow.getQuestId(),
                new QuestTally(JpaExplorerProgressRepository.splitSet(questRow.getTally())), questRow.getClaimedAt()))
            .toList());
    }

    @Override
    public void save(QuestBoard board) {
        String id = board.explorerId().value();
        String period = board.period().value();
        Map<String, QuestRow> existing = questRows.findByExplorerIdAndQuestPeriod(id, period).stream()
            .collect(Collectors.toMap(QuestRow::getQuestId, Function.identity()));
        for (QuestProgress questProgress : board.rows()) {
            QuestRow questRow = Optional.ofNullable(existing.get(questProgress.questId()))
                .orElseGet(() -> new QuestRow(id, period, questProgress.questId()));
            questRow.setTally(String.join(",", questProgress.tally().sorted()));
            questRow.setCurrentCount(questProgress.tally().keys().size());
            questRow.setClaimedAt(questProgress.claimedAt());
            questRows.save(questRow);
        }
    }
}
