package com.kobi.territory.wardrobe.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.wardrobe.domain.inventory.OwnedItem;
import com.kobi.territory.wardrobe.domain.inventory.VisitKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** owned_item_basis — 체크인 아이템을 뒷받침하는 활성 방문(지역, 지도) 한 개. */
@Entity
@Table(name = "owned_item_basis")
@IdClass(OwnedItemBasisJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnedItemBasisJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Id
    @Column(name = "item_id", length = 60)
    private String itemId;

    @Id
    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Id
    @Column(name = "map_id", length = 36)
    private String mapId;

    /** 아이템 하나의 근거 행 전부. */
    public static List<OwnedItemBasisJpaEntity> allOf(ExplorerId explorer, OwnedItem item) {
        return item.basis().stream().map(visit -> {
            OwnedItemBasisJpaEntity entity = new OwnedItemBasisJpaEntity();
            entity.explorerId = explorer.value();
            entity.itemId = item.itemId();
            entity.regionCode = visit.region().value();
            entity.mapId = visit.mapId();
            return entity;
        }).toList();
    }

    public String itemId() {
        return itemId;
    }

    public VisitKey toDomain() {
        return new VisitKey(RegionCode.of(regionCode), mapId);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String explorerId;
        private String itemId;
        private String regionCode;
        private String mapId;
    }
}
