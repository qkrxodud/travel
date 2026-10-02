package com.kobi.territory.exploration.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface VisitJpaRepository extends JpaRepository<VisitJpaEntity, Long> {

    List<VisitJpaEntity> findByMapId(String mapId);
}
