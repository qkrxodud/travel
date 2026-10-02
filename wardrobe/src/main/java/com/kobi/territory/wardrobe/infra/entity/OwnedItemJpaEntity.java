package com.kobi.territory.wardrobe.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.wardrobe.domain.item.GrantKind;
import com.kobi.territory.wardrobe.domain.inventory.OwnedItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** owned_item — 보유 아이템 한 개(OwnedItem). PK(explorer_id, item_id) 가 "같은 itemId 한 번만"을 DB 에서도 지킨다. 근거는 owned_item_basis. */
@Entity
@Table(name = "owned_item")
@IdClass(OwnedItemJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnedItemJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Id
    @Column(name = "item_id", length = 60)
    private String itemId;

    @Column(nullable = false, length = 12)
    private String source;

    @Column(name = "grant_kind", nullable = false, length = 20)
    private String grantKind;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    @Column(nullable = false)
    private boolean favorite;

    public static OwnedItemJpaEntity from(ExplorerId explorer, OwnedItem item) {
        OwnedItemJpaEntity entity = new OwnedItemJpaEntity();
        entity.explorerId = explorer.value();
        entity.itemId = item.itemId();
        entity.apply(item);
        return entity;
    }

    public void apply(OwnedItem item) {
        this.source = item.source().name();
        this.grantKind = item.grantKind().name();
        this.acquiredAt = item.acquiredAt();
        this.favorite = item.favorite();
    }

    public static Key keyOf(ExplorerId explorer, String itemId) {
        return new Key(explorer.value(), itemId);
    }

    public String itemId() {
        return itemId;
    }

    /** 이 아이템의 근거 행(owned_item_basis)과 함께 복원한다. basisRows 는 이 탐험가의 근거 행 전체여도 된다(아이템으로 거른다). */
    public OwnedItem toDomain(List<OwnedItemBasisJpaEntity> basisRows) {
        return OwnedItem.restore(itemId, GrantKind.valueOf(grantKind), acquiredAt, favorite, basisRows.stream()
            .filter(basisRow -> basisRow.itemId().equals(itemId)).map(OwnedItemBasisJpaEntity::toDomain)
            .collect(Collectors.toSet()));
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String explorerId;
        private String itemId;
    }
}
