package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.QuestProgressJpaEntity;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.domain.quest.QuestBoard;
import com.kobi.territory.progression.domain.quest.QuestBoardRepository;
import com.kobi.territory.progression.domain.quest.QuestPeriod;
import com.kobi.territory.progression.domain.quest.QuestProgress;
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

    /** 변경 반영: 새 퀘스트 행은 추가, 있던 행은 갱신(version 낙관적 락). */
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

    /**
     * 재계산 결과로 바꾸기: 행마다 apply(없으면 추가) 후 flush — 도감과 같은 이유(구조 QA S2-1)로 지우고 다시 넣지 않는다.
     * 재계산이 읽은 뒤 바뀐 행은 version 충돌 → 재계산 재시도. 재계산 결과에 없는 행(그 뒤에 새로 생긴 행)은 그대로 둔다.
     */
    @Override
    public void replace(QuestBoard board) {
        save(board);
        questRows.flush();
    }
}
