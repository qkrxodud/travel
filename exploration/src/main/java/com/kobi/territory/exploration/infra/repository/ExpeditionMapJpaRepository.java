package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.ExpeditionMapJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExpeditionMapJpaRepository extends JpaRepository<ExpeditionMapJpaEntity, String> {

    Optional<ExpeditionMapJpaEntity> findFirstByOwnerIdAndKind(String ownerId, String kind);

    boolean existsByInviteCode(String inviteCode);
}
