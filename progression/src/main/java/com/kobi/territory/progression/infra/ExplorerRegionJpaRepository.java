package com.kobi.territory.progression.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExplorerRegionJpaRepository extends JpaRepository<ExplorerRegionJpaEntity, ExplorerRegionJpaEntity.Key> {

    List<ExplorerRegionJpaEntity> findByExplorerId(String explorerId);
}
