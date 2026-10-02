package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.ThemeProgressJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ThemeProgressJpaRepository extends JpaRepository<ThemeProgressJpaEntity, ThemeProgressJpaEntity.Key> {

    List<ThemeProgressJpaEntity> findByMapId(String mapId);
}
