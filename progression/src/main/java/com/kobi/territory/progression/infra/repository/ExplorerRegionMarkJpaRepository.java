package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.ExplorerRegionMarkJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExplorerRegionMarkJpaRepository extends JpaRepository<ExplorerRegionMarkJpaEntity, ExplorerRegionMarkJpaEntity.Key> {

    List<ExplorerRegionMarkJpaEntity> findByExplorerId(String explorerId);
}
