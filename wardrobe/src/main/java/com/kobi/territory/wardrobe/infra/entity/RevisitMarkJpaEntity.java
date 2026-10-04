package com.kobi.territory.wardrobe.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.wardrobe.domain.inventory.RevisitMark;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** inventory_revisit — 재방문 도장을 받은 지역(RevisitMark, Inventory 의 자식, 9단계). 지우지 않는다(재계산도 유지). */
@Entity
@Table(name = "inventory_revisit")
@IdClass(RevisitMarkJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevisitMarkJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Id
    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(name = "marked_at", nullable = false)
    private Instant markedAt;

    public static RevisitMarkJpaEntity from(ExplorerId explorerId, RevisitMark mark) {
        RevisitMarkJpaEntity entity = new RevisitMarkJpaEntity();
        entity.explorerId = explorerId.value();
        entity.regionCode = mark.region().value();
        entity.markedAt = mark.markedAt();
        return entity;
    }

    public RevisitMark toDomain() {
        return new RevisitMark(RegionCode.of(regionCode), markedAt);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String explorerId;
        private String regionCode;
    }
}
