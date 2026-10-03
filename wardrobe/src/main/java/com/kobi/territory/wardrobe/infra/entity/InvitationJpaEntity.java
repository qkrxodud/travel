package com.kobi.territory.wardrobe.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.wardrobe.domain.inventory.InvitationRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** invite_reward — 초대 보상 기록(InvitationRecord, Inventory 의 자식). PK(초대받은 쪽, 초대한 쪽) = 같은 쌍 1회. */
@Entity
@Table(name = "invite_reward")
@IdClass(InvitationJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvitationJpaEntity {

    @Id
    @Column(name = "invitee_id", length = 36)
    private String inviteeId;

    @Id
    @Column(name = "inviter_id", length = 36)
    private String inviterId;

    @Column(name = "map_id", length = 36, nullable = false)
    private String mapId;

    @Column(name = "rewarded_at", nullable = false)
    private Instant rewardedAt;

    public static InvitationJpaEntity from(ExplorerId invitee, InvitationRecord invitation) {
        InvitationJpaEntity entity = new InvitationJpaEntity();
        entity.inviteeId = invitee.value();
        entity.inviterId = invitation.inviterId().value();
        entity.mapId = invitation.mapId();
        entity.rewardedAt = invitation.rewardedAt();
        return entity;
    }

    public InvitationRecord toDomain() {
        return new InvitationRecord(ExplorerId.of(inviterId), mapId, rewardedAt);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String inviteeId;
        private String inviterId;
    }
}
