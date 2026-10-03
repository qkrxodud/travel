package com.kobi.territory.social.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.domain.stats.RankPercentile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** rank_percentile — 전체 유저 중 상위 %(일 1회 배치 스냅숏). rank 는 MySQL 예약어라 rank_position. */
@Entity
@Table(name = "rank_percentile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankPercentileJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Column(name = "region_count", nullable = false)
    private int regionCount;

    @Column(name = "rank_position", nullable = false)
    private int rankPosition;

    @Column(nullable = false)
    private int population;

    @Column(name = "top_percent", nullable = false)
    private int topPercent;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public static RankPercentileJpaEntity from(RankPercentile percentile) {
        RankPercentileJpaEntity entity = new RankPercentileJpaEntity();
        entity.explorerId = percentile.explorerId().value();
        entity.regionCount = percentile.regionCount();
        entity.rankPosition = percentile.rank();
        entity.population = percentile.population();
        entity.topPercent = percentile.topPercent();
        entity.computedAt = percentile.computedAt();
        return entity;
    }

    public RankPercentile toDomain() {
        return new RankPercentile(ExplorerId.of(explorerId), regionCount, rankPosition, population, topPercent, computedAt);
    }
}
