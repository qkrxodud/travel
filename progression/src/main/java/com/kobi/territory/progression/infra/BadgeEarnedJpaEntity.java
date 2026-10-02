package com.kobi.territory.progression.infra;

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

/** badge_earned — 뱃지 획득(추가만). ExplorerProgress.badges 의 한 항목. */
@Entity
@Table(name = "badge_earned")
@IdClass(BadgeEarnedJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BadgeEarnedJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Id
    @Column(name = "badge_id", length = 20)
    private String badgeId;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    static BadgeEarnedJpaEntity from(ExplorerId explorer, String badgeId, Instant earnedAt) {
        BadgeEarnedJpaEntity entity = new BadgeEarnedJpaEntity();
        entity.explorerId = explorer.value();
        entity.badgeId = badgeId;
        entity.earnedAt = earnedAt;
        return entity;
    }

    String badgeId() {
        return badgeId;
    }

    Instant earnedAt() {
        return earnedAt;
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    static class Key implements Serializable {
        private String explorerId;
        private String badgeId;
    }
}
