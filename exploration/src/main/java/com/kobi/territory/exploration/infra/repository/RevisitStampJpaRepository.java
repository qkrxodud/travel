package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.RevisitStampJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface RevisitStampJpaRepository extends JpaRepository<RevisitStampJpaEntity, RevisitStampJpaEntity.Key> {

    List<RevisitStampJpaEntity> findByExplorerId(String explorerId);
}
