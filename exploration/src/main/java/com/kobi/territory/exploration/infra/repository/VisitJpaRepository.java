package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.VisitJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface VisitJpaRepository extends JpaRepository<VisitJpaEntity, Long> {

    List<VisitJpaEntity> findByMapId(String mapId);

    /** 이 탐험가의 보이는(숨기지 않은) 방문이 어느 지도에든 이 지역에 있는지. */
    boolean existsByCheckedInByAndRegionCodeAndHiddenAtIsNull(String checkedInBy, String regionCode);
}
