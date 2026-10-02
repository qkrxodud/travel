package com.kobi.territory.exploration.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.territory.VisitGenerations.VisitGeneration;
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

/** visit_generation 테이블 ↔ (지역, 멤버)의 마지막 체크인 회차(Territory 의 자식, 결정 6). 방문을 지워도 남는다. */
@Entity
@Table(name = "visit_generation")
@IdClass(VisitGenerationJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitGenerationJpaEntity {

    @Id
    @Column(name = "map_id", length = 36)
    private String mapId;

    @Id
    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Column(name = "last_generation", nullable = false)
    private int lastGeneration;

    public static VisitGenerationJpaEntity from(MapId mapId, VisitGeneration generation) {
        VisitGenerationJpaEntity entity = new VisitGenerationJpaEntity();
        entity.mapId = mapId.value();
        entity.regionCode = generation.region().value();
        entity.explorerId = generation.member().value();
        entity.lastGeneration = generation.last();
        return entity;
    }

    public void apply(VisitGeneration generation) {
        this.lastGeneration = generation.last();
    }

    public static Key key(MapId mapId, VisitGeneration generation) {
        return new Key(mapId.value(), generation.region().value(), generation.member().value());
    }

    public VisitGeneration toDomain() {
        return new VisitGeneration(RegionCode.of(regionCode), ExplorerId.of(explorerId), lastGeneration);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String mapId;
        private String regionCode;
        private String explorerId;
    }
}
