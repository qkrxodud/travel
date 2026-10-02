package com.kobi.territory.wardrobe.infra.repository;

import com.kobi.territory.wardrobe.infra.entity.OwnedItemBasisJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface OwnedItemBasisJpaRepository extends JpaRepository<OwnedItemBasisJpaEntity, OwnedItemBasisJpaEntity.Key> {
    List<OwnedItemBasisJpaEntity> findByExplorerId(String explorerId);

    List<OwnedItemBasisJpaEntity> findByExplorerIdAndItemId(String explorerId, String itemId);
}
