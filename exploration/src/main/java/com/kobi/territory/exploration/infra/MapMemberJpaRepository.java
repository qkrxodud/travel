package com.kobi.territory.exploration.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface MapMemberJpaRepository extends JpaRepository<MapMemberJpaEntity, MapMemberJpaEntity.Key> {

    List<MapMemberJpaEntity> findByMapId(String mapId);

    List<MapMemberJpaEntity> findByExplorerId(String explorerId);
}
