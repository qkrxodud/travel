package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.BadgeEarnedJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface BadgeEarnedJpaRepository extends JpaRepository<BadgeEarnedJpaEntity, BadgeEarnedJpaEntity.Key> {

    List<BadgeEarnedJpaEntity> findByExplorerId(String explorerId);
}
