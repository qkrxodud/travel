package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.TerritoryJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TerritoryJpaRepository extends JpaRepository<TerritoryJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select territory from TerritoryJpaEntity territory where territory.mapId = :mapId")
    Optional<TerritoryJpaEntity> lockByMapId(@Param("mapId") String mapId);

    /** 개인 지도의 Territory 행 잠금. 잠금 전 일반 SELECT를 하지 않도록 한 문장(locking read)으로 찾는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select territory from TerritoryJpaEntity territory where territory.mapId in "
        + "(select map.id from ExpeditionMapJpaEntity map where map.ownerId = :ownerId and map.kind = 'PERSONAL')")
    List<TerritoryJpaEntity> lockPersonal(@Param("ownerId") String ownerId);
}
