package com.kobi.territory.wardrobe.infra.repository;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.wardrobe.domain.inventory.Inventory;
import com.kobi.territory.wardrobe.domain.inventory.InventoryRepository;
import com.kobi.territory.wardrobe.domain.inventory.OwnedItem;
import com.kobi.territory.wardrobe.domain.inventory.VisitTrace;
import com.kobi.territory.wardrobe.infra.entity.InventoryJpaEntity;
import com.kobi.territory.wardrobe.infra.entity.OwnedItemBasisJpaEntity;
import com.kobi.territory.wardrobe.infra.entity.OwnedItemJpaEntity;
import com.kobi.territory.wardrobe.infra.entity.VisitTraceJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Inventory 저장소 어댑터 — inventory(루트)·owned_item·owned_item_basis·inventory_visit. "어떻게 저장할지"만 한다.
 * <ul>
 *   <li>{@link #save}: 복원 이후 새로 생긴 행 insert, 바뀐 아이템 update(근거 행은 지우고 다시 넣기), 회수된 행 delete.</li>
 *   <li>{@link #replace}: 그 탐험가의 자식 행을 지우고 애그리거트 상태로 다시 넣는다(재계산 경로).</li>
 * </ul>
 * 루트 행은 저장마다 version 을 강제로 올린다 — 자식 행만 바뀌어도 동시 갱신(이벤트 처리 vs 즐겨찾기 vs 재계산)이 충돌로 드러나게.
 * 잠가 읽은 루트(findLocked)는 PESSIMISTIC_FORCE_INCREMENT, 잠그지 않고 읽은 루트는 OPTIMISTIC_FORCE_INCREMENT(진행 저장소와 같은 방식).
 */
@Repository
class JpaInventoryRepository implements InventoryRepository {

    private final InventoryJpaRepository inventoryRows;
    private final OwnedItemJpaRepository itemRows;
    private final OwnedItemBasisJpaRepository basisRows;
    private final VisitTraceJpaRepository traceRows;
    private final EntityManager entityManager;

    JpaInventoryRepository(InventoryJpaRepository inventoryRows, OwnedItemJpaRepository itemRows,
                           OwnedItemBasisJpaRepository basisRows, VisitTraceJpaRepository traceRows,
                           EntityManager entityManager) {
        this.inventoryRows = inventoryRows;
        this.itemRows = itemRows;
        this.basisRows = basisRows;
        this.traceRows = traceRows;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Inventory> find(ExplorerId explorerId) {
        return inventoryRows.findById(explorerId.value()).map(this::withChildren);
    }

    @Override
    public Optional<Inventory> findLocked(ExplorerId explorerId) {
        return inventoryRows.lockById(explorerId.value()).map(this::withChildren);
    }

    private Inventory withChildren(InventoryJpaEntity root) {
        String id = root.explorerId();
        return root.toDomain(itemRows.findByExplorerId(id), basisRows.findByExplorerId(id), traceRows.findByExplorerId(id));
    }

    @Override
    public void save(Inventory inventory) {
        ExplorerId explorer = inventory.explorerId();
        saveRoot(inventory);
        inventory.ownedItems().removed().forEach(itemId -> {
            basisRows.deleteAll(basisRows.findByExplorerIdAndItemId(explorer.value(), itemId));
            itemRows.deleteById(OwnedItemJpaEntity.keyOf(explorer, itemId));
        });
        inventory.ownedItems().added().forEach(item -> insertItem(explorer, item));
        inventory.ownedItems().updated().forEach(item -> updateItem(explorer, item));
        inventory.visitTraces().changed().forEach(trace -> saveTrace(explorer, trace));
    }

    /**
     * 통째로 바꾸기(재계산). 호출자는 {@link #findLocked}로 루트를 이미 잠갔다(없던 탐험가면 새 루트를 넣는다). 자식 행을 지우고
     * flush 한 뒤 다시 넣는다(기존 행과 비교하지 않는다).
     */
    @Override
    public void replace(Inventory inventory) {
        String id = inventory.explorerId().value();
        basisRows.deleteAll(basisRows.findByExplorerId(id));
        itemRows.deleteAll(itemRows.findByExplorerId(id));
        traceRows.deleteAll(traceRows.findByExplorerId(id));
        entityManager.flush();
        inventory.ownedItems().all().forEach(item -> insertItem(inventory.explorerId(), item));
        inventory.visitTraces().all().forEach(trace -> entityManager.persist(VisitTraceJpaEntity.from(inventory.explorerId(), trace)));
        saveRoot(inventory);
    }

    private void saveRoot(Inventory inventory) {
        inventoryRows.findById(inventory.explorerId().value()).ifPresentOrElse(root -> {
            root.apply(inventory);
            entityManager.lock(root, entityManager.getLockMode(root) == LockModeType.PESSIMISTIC_WRITE
                ? LockModeType.PESSIMISTIC_FORCE_INCREMENT : LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }, () -> inventoryRows.save(InventoryJpaEntity.from(inventory)));
    }

    private void insertItem(ExplorerId explorer, OwnedItem item) {
        entityManager.persist(OwnedItemJpaEntity.from(explorer, item));
        OwnedItemBasisJpaEntity.allOf(explorer, item).forEach(entityManager::persist);
    }

    private void updateItem(ExplorerId explorer, OwnedItem item) {
        itemRows.findById(OwnedItemJpaEntity.keyOf(explorer, item.itemId())).ifPresentOrElse(
            itemRow -> itemRow.apply(item),
            () -> entityManager.persist(OwnedItemJpaEntity.from(explorer, item)));
        basisRows.deleteAll(basisRows.findByExplorerIdAndItemId(explorer.value(), item.itemId()));
        entityManager.flush();
        OwnedItemBasisJpaEntity.allOf(explorer, item).forEach(entityManager::persist);
    }

    private void saveTrace(ExplorerId explorer, VisitTrace trace) {
        traceRows.findById(VisitTraceJpaEntity.keyOf(explorer, trace)).ifPresentOrElse(
            traceRow -> traceRow.apply(trace),
            () -> entityManager.persist(VisitTraceJpaEntity.from(explorer, trace)));
    }
}
