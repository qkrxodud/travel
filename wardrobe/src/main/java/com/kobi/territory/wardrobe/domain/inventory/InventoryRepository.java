package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Optional;

/**
 * 보유 아이템 저장소(애그리거트 단위) — inventory(루트)·owned_item·owned_item_basis·inventory_visit. 루트 version 으로 낙관적 락
 * (자식 행만 바뀌어도 증가). save 는 복원 이후 바뀐 행만 반영한다.
 */
public interface InventoryRepository {

    Optional<Inventory> find(ExplorerId explorerId);

    /** 루트 행을 배타 잠금(PESSIMISTIC_WRITE)한 뒤 불러온다 — 이벤트 처리·재계산이 같은 잠금으로 직렬화된다. */
    Optional<Inventory> findLocked(ExplorerId explorerId);

    /** 평소 커맨드: 복원 이후 바뀐 행만 반영. */
    void save(Inventory inventory);

    /** 통째로 바꾸기(재계산). inventory 는 {@link #findLocked}로 불러온 것(또는 루트가 없던 탐험가)이어야 한다. */
    void replace(Inventory inventory);
}
