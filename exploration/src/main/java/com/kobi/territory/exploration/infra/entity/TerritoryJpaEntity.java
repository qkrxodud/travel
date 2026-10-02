package com.kobi.territory.exploration.infra.entity;

import com.kobi.territory.exploration.domain.map.MapId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * territory 테이블 — Territory 루트 행(지도 단위 직렬화 잠금 대상). 방문은 visit 자식 행이고, 애그리거트 복원은
 * 방문 행만으로 한다(이 행은 잠금·존재 확인용).
 */
@Entity
@Table(name = "territory")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TerritoryJpaEntity {

    @Id
    @Column(name = "map_id", length = 36)
    private String mapId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static TerritoryJpaEntity create(MapId mapId, Instant createdAt) {
        TerritoryJpaEntity entity = new TerritoryJpaEntity();
        entity.mapId = mapId.value();
        entity.createdAt = createdAt;
        return entity;
    }

    public MapId mapId() {
        return MapId.of(mapId);
    }
}
