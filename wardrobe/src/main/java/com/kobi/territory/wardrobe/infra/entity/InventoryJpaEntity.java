package com.kobi.territory.wardrobe.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.wardrobe.domain.inventory.Inventory;
import com.kobi.territory.wardrobe.domain.inventory.OwnedItems;
import com.kobi.territory.wardrobe.domain.inventory.VisitTraces;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * inventory — Inventory 루트 행. version 으로 낙관적 락(자식 행 owned_item·owned_item_basis·inventory_visit 만 바뀌어도 저장소가 강제 증가).
 * 애그리거트는 이 행과 자식 행으로 복원한다({@link #toDomain}).
 */
@Entity
@Table(name = "inventory")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static InventoryJpaEntity from(Inventory inventory) {
        InventoryJpaEntity entity = new InventoryJpaEntity();
        entity.explorerId = inventory.explorerId().value();
        entity.apply(inventory);
        return entity;
    }

    public void apply(Inventory inventory) {
        this.updatedAt = inventory.updatedAt();
    }

    public String explorerId() {
        return explorerId;
    }

    public Inventory toDomain(List<OwnedItemJpaEntity> itemRows, List<OwnedItemBasisJpaEntity> basisRows,
                              List<VisitTraceJpaEntity> traceRows) {
        return Inventory.restore(ExplorerId.of(explorerId),
            OwnedItems.of(itemRows.stream().map(itemRow -> itemRow.toDomain(basisRows)).toList()),
            VisitTraces.of(traceRows.stream().map(VisitTraceJpaEntity::toDomain).toList()), updatedAt);
    }
}
