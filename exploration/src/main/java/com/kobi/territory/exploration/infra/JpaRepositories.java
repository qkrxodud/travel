package com.kobi.territory.exploration.infra;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ExplorerJpaRepository extends JpaRepository<ExplorerJpaEntity, String> {}

interface ExpeditionMapJpaRepository extends JpaRepository<ExpeditionMapJpaEntity, String> {

    Optional<ExpeditionMapJpaEntity> findFirstByOwnerIdAndKind(String ownerId, String kind);

    boolean existsByInviteCode(String inviteCode);
}

interface TerritoryJpaRepository extends JpaRepository<TerritoryJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TerritoryJpaEntity t where t.mapId = :mapId")
    Optional<TerritoryJpaEntity> lockByMapId(@Param("mapId") String mapId);

    /** 개인 지도의 Territory 행 잠금. 잠금 전 일반 SELECT를 하지 않도록 한 문장(locking read)으로 찾는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TerritoryJpaEntity t where t.mapId in "
        + "(select m.id from ExpeditionMapJpaEntity m where m.ownerId = :ownerId and m.kind = 'PERSONAL')")
    List<TerritoryJpaEntity> lockPersonal(@Param("ownerId") String ownerId);
}

interface MapMemberJpaRepository extends JpaRepository<MapMemberJpaEntity, MapMemberJpaEntity.Key> {
    List<MapMemberJpaEntity> findByMapId(String mapId);
}

interface VisitJpaRepository extends JpaRepository<VisitJpaEntity, Long> {
    List<VisitJpaEntity> findByMapId(String mapId);
}
