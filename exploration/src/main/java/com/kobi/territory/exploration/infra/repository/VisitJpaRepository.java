package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.VisitJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface VisitJpaRepository extends JpaRepository<VisitJpaEntity, Long> {

    List<VisitJpaEntity> findByMapId(String mapId);
}
