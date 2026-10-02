package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.ExpeditionMapJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExpeditionMapJpaRepository extends JpaRepository<ExpeditionMapJpaEntity, String> {

    Optional<ExpeditionMapJpaEntity> findFirstByOwnerIdAndKind(String ownerId, String kind);

    /** id 만 읽는다(엔티티를 영속성 컨텍스트에 올리지 않음 — 이어지는 잠금 조회가 최신 행을 받게). */
    @Query("select map.id from ExpeditionMapJpaEntity map where map.inviteCode = :inviteCode")
    Optional<String> findIdByInviteCode(@Param("inviteCode") String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select map from ExpeditionMapJpaEntity map where map.id = :id")
    Optional<ExpeditionMapJpaEntity> lockSharedById(@Param("id") String id);

    @Query("select map.id from ExpeditionMapJpaEntity map where map.ownerId = :ownerId and map.kind = 'PERSONAL'")
    List<String> findPersonalMapIds(@Param("ownerId") String ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select map from ExpeditionMapJpaEntity map where map.id = :id")
    Optional<ExpeditionMapJpaEntity> lockById(@Param("id") String id);

    boolean existsByInviteCode(String inviteCode);
}
