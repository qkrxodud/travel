package com.kobi.territory.progression.infra;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.domain.QuestBoard;
import com.kobi.territory.progression.domain.QuestBoardRepository;
import com.kobi.territory.progression.domain.QuestPeriod;
import com.kobi.territory.progression.domain.QuestProgress;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** QuestBoard 저장소 어댑터 — quest_progress. 행 ↔ 도메인 변환은 QuestProgressJpaEntity 가 한다. */
@Repository
class JpaQuestBoardRepository implements QuestBoardRepository {

    private final QuestProgressJpaRepository questRows;

    JpaQuestBoardRepository(QuestProgressJpaRepository questRows) {
        this.questRows = questRows;
    }

    @Override
    public QuestBoard load(ExplorerId explorerId, QuestPeriod period) {
        return QuestProgressJpaEntity.toQuestBoard(explorerId, period,
            questRows.findByExplorerIdAndQuestPeriod(explorerId.value(), period.value()));
    }

    @Override
    public List<QuestBoard> loadAll(ExplorerId explorerId) {
        return questRows.findByExplorerId(explorerId.value()).stream()
            .collect(Collectors.groupingBy(QuestProgressJpaEntity::period)).entrySet().stream()
            .map(byPeriod -> QuestProgressJpaEntity.toQuestBoard(explorerId, byPeriod.getKey(), byPeriod.getValue()))
            .toList();
    }

    @Override
    public void save(QuestBoard board) {
        Map<String, QuestProgressJpaEntity> saved = questRows
            .findByExplorerIdAndQuestPeriod(board.explorerId().value(), board.period().value()).stream()
            .collect(Collectors.toMap(QuestProgressJpaEntity::questId, Function.identity()));
        for (QuestProgress questProgress : board.rows()) {
            QuestProgressJpaEntity questRow = saved.get(questProgress.questId());
            if (questRow == null) {
                questRows.save(QuestProgressJpaEntity.from(board.explorerId(), board.period(), questProgress));
            } else {
                questRow.apply(questProgress);
            }
        }
    }
}
