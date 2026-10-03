package com.kobi.territory.social.infra.entity;

import com.kobi.territory.social.domain.stats.ProvinceStat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** province_stats — 시·도 평균 유저(일 1회 배치 스냅숏, 'ALL' = 전국). 평균은 도메인이 합 / 수로 낸다(부동소수 컬럼 없음). */
@Entity
@Table(name = "province_stats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProvinceStatJpaEntity {

    @Id
    @Column(name = "province_code", length = 5)
    private String provinceCode;

    @Column(name = "explorer_count", nullable = false)
    private int explorerCount;

    @Column(name = "region_count_sum", nullable = false)
    private long regionCountSum;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public static ProvinceStatJpaEntity from(ProvinceStat stat) {
        ProvinceStatJpaEntity entity = new ProvinceStatJpaEntity();
        entity.provinceCode = stat.provinceCode();
        entity.explorerCount = stat.explorerCount();
        entity.regionCountSum = stat.regionCountSum();
        entity.computedAt = stat.computedAt();
        return entity;
    }

    public ProvinceStat toDomain() {
        return new ProvinceStat(provinceCode, explorerCount, regionCountSum, computedAt);
    }
}
