package com.kobi.territory.progression.infra;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.domain.ExplorerProgress;
import com.kobi.territory.progression.domain.Streak;
import com.kobi.territory.progression.domain.XpLedger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * explorer_progress — ExplorerProgress 루트 행. version 으로 낙관적 락. 애그리거트는 이 행과 자식 행
 * (xp_ledger·explorer_region·badge_earned·title_earned)으로 복원한다({@link #toDomain}).
 */
@Entity
@Table(name = "explorer_progress")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ExplorerProgressJpaEntity {

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

    static ExplorerProgressJpaEntity from(ExplorerProgress progress, Instant now) {
        ExplorerProgressJpaEntity entity = new ExplorerProgressJpaEntity();
        entity.explorerId = progress.explorerId().value();
        entity.apply(progress, now);
        return entity;
    }

    /** 루트 행 값(XP·레벨·선택 칭호·스트릭)을 도메인 상태로 맞춘다. */
    void apply(ExplorerProgress progress, Instant now) {
        this.xp = progress.xp();
        this.level = progress.level();
        this.titleId = progress.selectedTitle().orElse(null);
        this.streakMonths = progress.streak().months();
        this.streakLastMonth = progress.streak().lastMonth() == null ? null : progress.streak().lastMonth().toString();
        this.updatedAt = now;
    }

    /** 루트 + 자식 행으로 애그리거트를 복원한다. */
    ExplorerProgress toDomain(List<XpLedgerJpaEntity> ledgerRows, List<ExplorerRegionJpaEntity> regionRows,
                              List<BadgeEarnedJpaEntity> badgeRows, List<TitleEarnedJpaEntity> titleRows) {
        Map<String, Instant> badges = new LinkedHashMap<>();
        badgeRows.forEach(badgeRow -> badges.putIfAbsent(badgeRow.badgeId(), badgeRow.earnedAt()));
        Map<String, Instant> titles = new LinkedHashMap<>();
        titleRows.forEach(titleRow -> titles.putIfAbsent(titleRow.titleId(), titleRow.earnedAt()));
        return ExplorerProgress.restore(ExplorerId.of(explorerId),
            XpLedger.of(ledgerRows.stream().map(XpLedgerJpaEntity::toDomain).toList()),
            ExplorerRegionJpaEntity.toDomain(regionRows),
            streakMonths == 0 ? Streak.NONE : new Streak(streakMonths, YearMonth.parse(streakLastMonth)),
            badges, titles, titleId, level);
    }
}
