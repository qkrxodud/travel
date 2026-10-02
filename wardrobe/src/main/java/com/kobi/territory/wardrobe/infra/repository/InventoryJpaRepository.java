package com.kobi.territory.wardrobe.infra.repository;

import com.kobi.territory.wardrobe.infra.entity.InventoryJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, String> {

    /** 루트 행 배타 잠금(SELECT … FOR UPDATE, 대기). 이벤트 처리와 재계산이 같은 잠금으로 시작한다(잠금 순서: 루트 → 자식). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from InventoryJpaEntity inventory where inventory.explorerId = :explorerId")
    Optional<InventoryJpaEntity> lockById(@Param("explorerId") String explorerId);
}
