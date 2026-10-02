package com.kobi.territory.exploration.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.map.Departure;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.Member;
import com.kobi.territory.exploration.domain.map.MemberRole;
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

/**
 * map_member 테이블 ↔ ExpeditionMap 의 자식: 현재 멤버(Member, left_at NULL) 또는 탈퇴 유예 중(Departure, left_at 있음).
 * 변환은 이 엔티티가 가진다.
 */
@Entity
@Table(name = "map_member")
@IdClass(MapMemberJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapMemberJpaEntity {

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

    @Column(name = "left_at")
    private Instant leftAt;

    public static MapMemberJpaEntity from(MapId mapId, Member member) {
        MapMemberJpaEntity entity = new MapMemberJpaEntity();
        entity.mapId = mapId.value();
        entity.explorerId = member.explorerId().value();
        entity.apply(member);
        return entity;
    }

    public static MapMemberJpaEntity from(MapId mapId, Departure departure) {
        MapMemberJpaEntity entity = new MapMemberJpaEntity();
        entity.mapId = mapId.value();
        entity.explorerId = departure.explorerId().value();
        entity.apply(departure);
        return entity;
    }

    public void apply(Member member) {
        this.role = member.role().name();
        this.joinedAt = member.joinedAt();
        this.leftAt = null;
    }

    public void apply(Departure departure) {
        this.role = MemberRole.MEMBER.name();
        this.joinedAt = departure.joinedAt();
        this.leftAt = departure.leftAt();
    }

    public MapId mapId() {
        return MapId.of(mapId);
    }

    public ExplorerId explorerId() {
        return ExplorerId.of(explorerId);
    }

    public boolean departed() {
        return leftAt != null;
    }

    public Member toDomain() {
        return new Member(explorerId(), MemberRole.valueOf(role), joinedAt);
    }

    public Departure toDeparture() {
        return new Departure(explorerId(), joinedAt, leftAt);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String mapId;
        private String explorerId;
    }
}
