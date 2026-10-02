package com.kobi.territory.exploration.infra;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.MapId;
import com.kobi.territory.exploration.domain.Member;
import com.kobi.territory.exploration.domain.MemberRole;
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

/** map_member 테이블 ↔ Member(ExpeditionMap 의 자식). 변환은 이 엔티티가 가진다. */
@Entity
@Table(name = "map_member")
@IdClass(MapMemberJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MapMemberJpaEntity {

    @Id
    @Column(name = "map_id", length = 36)
    private String mapId;

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    static MapMemberJpaEntity from(MapId mapId, Member member) {
        MapMemberJpaEntity entity = new MapMemberJpaEntity();
        entity.mapId = mapId.value();
        entity.explorerId = member.explorerId().value();
        entity.apply(member);
        return entity;
    }

    void apply(Member member) {
        this.role = member.role().name();
        this.joinedAt = member.joinedAt();
    }

    MapId mapId() {
        return MapId.of(mapId);
    }

    ExplorerId explorerId() {
        return ExplorerId.of(explorerId);
    }

    Member toDomain() {
        return new Member(explorerId(), MemberRole.valueOf(role), joinedAt);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    static class Key implements Serializable {
        private String mapId;
        private String explorerId;
    }
}
