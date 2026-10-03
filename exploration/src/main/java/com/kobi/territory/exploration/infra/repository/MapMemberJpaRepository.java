package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.MapMemberJpaEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MapMemberJpaRepository extends JpaRepository<MapMemberJpaEntity, MapMemberJpaEntity.Key> {

    List<MapMemberJpaEntity> findByMapId(String mapId);

    /** 현재 멤버인 행(탈퇴 유예 중 제외). */
    List<MapMemberJpaEntity> findByExplorerIdAndLeftAtIsNull(String explorerId);

    @Query("select member.mapId from MapMemberJpaEntity member, ExpeditionMapJpaEntity map where map.id = member.mapId "
        + "and member.explorerId = :explorerId and member.leftAt is null and map.kind = 'SHARED' order by map.createdAt, map.id")
    List<String> sharedMapIdsOf(@Param("explorerId") String explorerId);

    boolean existsByMapIdAndExplorerIdAndLeftAtIsNull(String mapId, String explorerId);

    @Query("select distinct member.mapId from MapMemberJpaEntity member where member.leftAt is not null and member.leftAt <= :leftBefore")
    List<String> mapIdsWithDeparturesBefore(@Param("leftBefore") Instant leftBefore);
}
