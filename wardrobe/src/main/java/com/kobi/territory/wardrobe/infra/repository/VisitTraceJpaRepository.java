package com.kobi.territory.wardrobe.infra.repository;

import com.kobi.territory.wardrobe.infra.entity.VisitTraceJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface VisitTraceJpaRepository extends JpaRepository<VisitTraceJpaEntity, VisitTraceJpaEntity.Key> {
    List<VisitTraceJpaEntity> findByExplorerId(String explorerId);
}
