package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.StreakFreezeJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface StreakFreezeJpaRepository extends JpaRepository<StreakFreezeJpaEntity, Long> {

    List<StreakFreezeJpaEntity> findByExplorerIdOrderByIdAsc(String explorerId);
}
