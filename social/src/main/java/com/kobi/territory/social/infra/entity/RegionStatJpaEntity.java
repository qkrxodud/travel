package com.kobi.territory.social.infra.entity;

import com.kobi.territory.social.domain.stats.RegionStat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** region_stats — 지역별 방문자 수(일 1회 배치 스냅숏). 비율은 도메인이 visitor_count / population 으로 낸다. */
@Entity
@Table(name = "region_stats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionStatJpaEntity {

    @Id
    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(name = "visitor_count", nullable = false)
    private int visitorCount;

    @Column(nullable = false)
    private int population;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public static RegionStatJpaEntity from(RegionStat stat) {
        RegionStatJpaEntity entity = new RegionStatJpaEntity();
        entity.regionCode = stat.regionCode();
        entity.visitorCount = stat.visitorCount();
        entity.population = stat.population();
        entity.computedAt = stat.computedAt();
        return entity;
    }

    public RegionStat toDomain() {
        return new RegionStat(regionCode, visitorCount, population, computedAt);
    }
}
