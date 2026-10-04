package com.kobi.territory.wardrobe.infra.repository;

import com.kobi.territory.wardrobe.infra.entity.RevisitMarkJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface RevisitMarkJpaRepository extends JpaRepository<RevisitMarkJpaEntity, RevisitMarkJpaEntity.Key> {
    List<RevisitMarkJpaEntity> findByExplorerId(String explorerId);
}
