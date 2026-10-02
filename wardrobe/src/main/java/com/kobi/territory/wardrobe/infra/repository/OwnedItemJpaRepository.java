package com.kobi.territory.wardrobe.infra.repository;

import com.kobi.territory.wardrobe.infra.entity.OwnedItemJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface OwnedItemJpaRepository extends JpaRepository<OwnedItemJpaEntity, OwnedItemJpaEntity.Key> {
    List<OwnedItemJpaEntity> findByExplorerId(String explorerId);
}
