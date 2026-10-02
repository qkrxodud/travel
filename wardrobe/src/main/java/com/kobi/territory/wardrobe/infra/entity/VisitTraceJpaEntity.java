package com.kobi.territory.wardrobe.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.wardrobe.domain.inventory.VisitTrace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** inventory_visit — 방문 흔적(VisitTrace): (지역, 지도)별 마지막 반영 세대·활성 여부. */
@Entity
@Table(name = "inventory_visit")
@IdClass(VisitTraceJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitTraceJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Id
    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Id
    @Column(name = "map_id", length = 36)
    private String mapId;

    @Column(nullable = false)
    private long generation;

    @Column(nullable = false)
    private boolean active;

    public static VisitTraceJpaEntity from(ExplorerId explorer, VisitTrace trace) {
        VisitTraceJpaEntity entity = new VisitTraceJpaEntity();
        entity.explorerId = explorer.value();
        entity.regionCode = trace.region().value();
        entity.mapId = trace.mapId();
        entity.apply(trace);
        return entity;
    }

    public void apply(VisitTrace trace) {
        this.generation = trace.generation();
        this.active = trace.active();
    }

    public static Key keyOf(ExplorerId explorer, VisitTrace trace) {
        return new Key(explorer.value(), trace.region().value(), trace.mapId());
    }

    public VisitTrace toDomain() {
        return VisitTrace.restore(RegionCode.of(regionCode), mapId, generation, active);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String explorerId;
        private String regionCode;
        private String mapId;
    }
}
