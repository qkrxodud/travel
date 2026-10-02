package com.kobi.territory.progression.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 진행 컨텍스트 JPA 엔티티(V2). 도메인과의 변환은 각 리포지토리 어댑터가 한다. */
final class ProgressJpaEntities {

    private ProgressJpaEntities() {}

    /** explorer_progress — ExplorerProgress 루트 행. version 으로 낙관적 락. */
    @Entity
    @Table(name = "explorer_progress")
    @Getter
    @Setter(AccessLevel.PACKAGE)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    static class ProgressRow {
        @Id
        @Column(name = "explorer_id", length = 36)
        private String explorerId;

        @Column(nullable = false)
        private long xp;

        @Column(nullable = false)
        private int level;

        @Column(name = "title_id", length = 40)
        private String titleId;

        @Column(name = "streak_months", nullable = false)
        private int streakMonths;

        @Column(name = "streak_last_month", length = 7)
        private String streakLastMonth;

        @Version
        private Long version;

        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;

        ProgressRow(String explorerId) {
            this.explorerId = explorerId;
        }
    }

    /** xp_ledger — 장부 한 줄. ref_id UNIQUE 가 멱등 키. */
    @Entity
    @Table(name = "xp_ledger")
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    static class LedgerRow {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "explorer_id", nullable = false, length = 36)
        private String explorerId;

        @Column(nullable = false, length = 20)
        private String source;

        @Column(nullable = false)
        private int amount;

        @Column(name = "ref_id", nullable = false, unique = true, length = 160)
        private String refId;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        LedgerRow(String explorerId, String source, int amount, String refId, Instant createdAt) {
            this.explorerId = explorerId;
            this.source = source;
            this.amount = amount;
            this.refId = refId;
            this.createdAt = createdAt;
        }
    }

    /** badge_earned — 뱃지(추가만). */
    @Entity
    @Table(name = "badge_earned")
    @IdClass(BadgeRow.Key.class)
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    static class BadgeRow {
        @Id
        @Column(name = "explorer_id", length = 36)
        private String explorerId;

        @Id
        @Column(name = "badge_id", length = 20)
        private String badgeId;

        @Column(name = "earned_at", nullable = false)
        private Instant earnedAt;

        BadgeRow(String explorerId, String badgeId, Instant earnedAt) {
            this.explorerId = explorerId;
            this.badgeId = badgeId;
            this.earnedAt = earnedAt;
        }

        @EqualsAndHashCode
        @NoArgsConstructor
        @AllArgsConstructor
        static class Key implements Serializable {
            private String explorerId;
            private String badgeId;
        }
    }

    /** title_earned — 칭호(추가만). §4 에 없는 테이블: 칭호도 뱃지처럼 "추가만"이라 따로 기록한다. */
    @Entity
    @Table(name = "title_earned")
    @IdClass(TitleRow.Key.class)
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    static class TitleRow {
        @Id
        @Column(name = "explorer_id", length = 36)
        private String explorerId;

        @Id
        @Column(name = "title_id", length = 40)
        private String titleId;

        @Column(name = "earned_at", nullable = false)
        private Instant earnedAt;

        TitleRow(String explorerId, String titleId, Instant earnedAt) {
            this.explorerId = explorerId;
            this.titleId = titleId;
            this.earnedAt = earnedAt;
        }

        @EqualsAndHashCode
        @NoArgsConstructor
        @AllArgsConstructor
        static class Key implements Serializable {
            private String explorerId;
            private String titleId;
        }
    }

    /** explorer_region — 탐험가 단위 지역(읽기 모델 겸 기본 XP 회수 판단, D2). */
    @Entity
    @Table(name = "explorer_region")
    @IdClass(RegionRow.Key.class)
    @Getter
    @Setter(AccessLevel.PACKAGE)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    static class RegionRow {
        @Id
        @Column(name = "explorer_id", length = 36)
        private String explorerId;

        @Id
        @Column(name = "region_code", length = 10)
        private String regionCode;

        @Column(name = "province_code", nullable = false, length = 5)
        private String provinceCode;

        @Column(nullable = false, length = 8)
        private String rarity;

        @Column(name = "first_visited_at", nullable = false)
        private Instant firstVisitedAt;

        @Column(name = "active_map_count", nullable = false)
        private int activeMapCount;

        @Column(name = "active_map_ids", nullable = false, length = 2000)
        private String activeMapIds;

        RegionRow(String explorerId, String regionCode) {
            this.explorerId = explorerId;
            this.regionCode = regionCode;
        }

        @EqualsAndHashCode
        @NoArgsConstructor
        @AllArgsConstructor
        static class Key implements Serializable {
            private String explorerId;
            private String regionCode;
        }
    }

    /** set_progress — 도감(지도 단위) 세트별 진행. */
    @Entity
    @Table(name = "set_progress")
    @IdClass(SetRow.Key.class)
    @Getter
    @Setter(AccessLevel.PACKAGE)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    static class SetRow {
        @Id
        @Column(name = "map_id", length = 36)
        private String mapId;

        @Id
        @Column(name = "set_id", length = 20)
        private String setId;

        @Column(name = "collected_codes", nullable = false, length = 1000)
        private String collectedCodes;

        @Column(name = "completed_at")
        private Instant completedAt;

        SetRow(String mapId, String setId) {
            this.mapId = mapId;
            this.setId = setId;
        }

        @EqualsAndHashCode
        @NoArgsConstructor
        @AllArgsConstructor
        static class Key implements Serializable {
            private String mapId;
            private String setId;
        }
    }

    /** quest_progress — 퀘스트 보드 행. quest_period = 'yyyy-MM' | 'ALL'. version 으로 동시 보상 받기 방지. */
    @Entity
    @Table(name = "quest_progress")
    @IdClass(QuestRow.Key.class)
    @Getter
    @Setter(AccessLevel.PACKAGE)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    static class QuestRow {
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

        QuestRow(String explorerId, String questPeriod, String questId) {
            this.explorerId = explorerId;
            this.questPeriod = questPeriod;
            this.questId = questId;
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
}
