package com.kobi.territory.wardrobe.infra.repository;

import com.kobi.territory.wardrobe.infra.entity.InvitationJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface InvitationJpaRepository extends JpaRepository<InvitationJpaEntity, InvitationJpaEntity.Key> {
    List<InvitationJpaEntity> findByInviteeId(String inviteeId);
}
