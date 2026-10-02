package com.kobi.territory.progression.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SetProgressJpaRepository extends JpaRepository<SetProgressJpaEntity, SetProgressJpaEntity.Key> {

    List<SetProgressJpaEntity> findByMapId(String mapId);
}
