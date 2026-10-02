package com.kobi.territory.exploration.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** territory 테이블 — Territory 루트 행(지도 단위 직렬화 잠금 대상). */
@Entity
@Table(name = "territory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class TerritoryJpaEntity {

    @Id
    @Column(name = "map_id", length = 36)
    private String mapId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    TerritoryJpaEntity(String mapId, Instant createdAt) {
        this.mapId = mapId;
        this.createdAt = createdAt;
    }
}
