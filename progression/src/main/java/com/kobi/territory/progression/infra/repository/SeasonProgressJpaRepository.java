package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.SeasonProgressJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SeasonProgressJpaRepository extends JpaRepository<SeasonProgressJpaEntity, SeasonProgressJpaEntity.Key> {

    List<SeasonProgressJpaEntity> findByMapId(String mapId);
}
