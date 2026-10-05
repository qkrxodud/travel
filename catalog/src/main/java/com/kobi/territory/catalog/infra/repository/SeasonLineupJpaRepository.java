package com.kobi.territory.catalog.infra.repository;

import com.kobi.territory.catalog.infra.entity.SeasonLineupJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonLineupJpaRepository extends JpaRepository<SeasonLineupJpaEntity, String> {}
