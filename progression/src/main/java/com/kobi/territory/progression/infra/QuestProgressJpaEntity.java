package com.kobi.territory.progression.infra;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.domain.QuestBoard;
import com.kobi.territory.progression.domain.QuestPeriod;
import com.kobi.territory.progression.domain.QuestProgress;
import com.kobi.territory.progression.domain.QuestTally;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * quest_progress — QuestBoard(explorerId, quest_period)의 퀘스트 하나. tally 는 "시·도|지역" 키(쉼표 구분),
 * current_count 는 센 지역 키 수(조회 편의). version 낙관적 락으로 동시 보상 받기를 막는다.
 */
@Entity
@Table(name = "quest_progress")
@IdClass(QuestProgressJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class QuestProgressJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Id
    @Column(name = "quest_period", length = 7)
    private String questPeriod;

    @Id
    @Column(name = "quest_id", length = 20)
    private String questId;

    @Column(name = "current_count", nullable = false)
    private int currentCount;

    @Column(nullable = false, length = 2000)
    private String tally;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Version
    private Long version;

    static QuestProgressJpaEntity from(ExplorerId explorer, QuestPeriod period, QuestProgress questProgress) {
        QuestProgressJpaEntity entity = new QuestProgressJpaEntity();
        entity.explorerId = explorer.value();
        entity.questPeriod = period.value();
        entity.questId = questProgress.questId();
        entity.apply(questProgress);
        return entity;
    }

    void apply(QuestProgress questProgress) {
        this.tally = CsvColumn.write(questProgress.tally().keys());
        this.currentCount = questProgress.tally().keys().size();
        this.claimedAt = questProgress.claimedAt();
    }

    String questId() {
        return questId;
    }

    QuestPeriod period() {
        return new QuestPeriod(questPeriod);
    }

    QuestProgress toDomain() {
        return new QuestProgress(questId, new QuestTally(CsvColumn.read(tally)), claimedAt);
    }

    /** 한 보드(탐험가·기간)의 행들로 QuestBoard 애그리거트를 복원한다. */
    static QuestBoard toQuestBoard(ExplorerId explorer, QuestPeriod period, List<QuestProgressJpaEntity> rows) {
        return QuestBoard.restore(explorer, period, rows.stream().map(QuestProgressJpaEntity::toDomain).toList());
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    static class Key implements Serializable {
        private String explorerId;
        private String questPeriod;
        private String questId;
    }
}
