package com.kobi.territory.progression.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.domain.progress.ExploredRegion;
import com.kobi.territory.progression.domain.progress.ExploredRegions;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** explorer_region — 탐험가 단위 지역(ExploredRegion). active_map_ids 는 활성 지도 id 집합(쉼표 구분). */
@Entity
@Table(name = "explorer_region")
@IdClass(ExplorerRegionJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExplorerRegionJpaEntity {

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

    public static ExplorerRegionJpaEntity from(ExplorerId explorer, ExploredRegion region) {
        ExplorerRegionJpaEntity entity = new ExplorerRegionJpaEntity();
        entity.explorerId = explorer.value();
        entity.regionCode = region.code().value();
        entity.apply(region);
        return entity;
    }

    public void apply(ExploredRegion region) {
        this.provinceCode = region.provinceCode();
        this.rarity = region.rarity().name();
        this.firstVisitedAt = region.firstVisitedAt();
        this.activeMapCount = region.activeMapCount();
        this.activeMapIds = CsvColumn.write(region.activeMaps());
    }

    public static Key keyOf(ExplorerId explorer, ExploredRegion region) {
        return new Key(explorer.value(), region.code().value());
    }

    public String regionCode() {
        return regionCode;
    }

    public ExploredRegion toDomain() {
        return ExploredRegion.restore(RegionCode.of(regionCode), provinceCode, Rarity.valueOf(rarity), firstVisitedAt,
            CsvColumn.read(activeMapIds));
    }

    public static ExploredRegions toDomain(List<ExplorerRegionJpaEntity> rows) {
        return ExploredRegions.of(rows.stream().map(ExplorerRegionJpaEntity::toDomain).toList());
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String explorerId;
        private String regionCode;
    }
}
