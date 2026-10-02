package com.kobi.territory.progression.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** title_earned — 칭호 획득(추가만). ExplorerProgress.titles 의 한 항목(§4 에 없는 2단계 추가 테이블). */
@Entity
@Table(name = "title_earned")
@IdClass(TitleEarnedJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TitleEarnedJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Id
    @Column(name = "title_id", length = 40)
    private String titleId;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    public static TitleEarnedJpaEntity from(ExplorerId explorer, String titleId, Instant earnedAt) {
        TitleEarnedJpaEntity entity = new TitleEarnedJpaEntity();
        entity.explorerId = explorer.value();
        entity.titleId = titleId;
        entity.earnedAt = earnedAt;
        return entity;
    }

    public String titleId() {
        return titleId;
    }

    public Instant earnedAt() {
        return earnedAt;
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String explorerId;
        private String titleId;
    }
}
