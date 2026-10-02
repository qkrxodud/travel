package com.kobi.territory.progression.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface BadgeEarnedJpaRepository extends JpaRepository<BadgeEarnedJpaEntity, BadgeEarnedJpaEntity.Key> {

    List<BadgeEarnedJpaEntity> findByExplorerId(String explorerId);
}
