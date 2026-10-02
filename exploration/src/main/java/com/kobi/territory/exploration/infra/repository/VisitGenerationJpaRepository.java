package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.VisitGenerationJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface VisitGenerationJpaRepository extends JpaRepository<VisitGenerationJpaEntity, VisitGenerationJpaEntity.Key> {

    List<VisitGenerationJpaEntity> findByMapId(String mapId);
}
