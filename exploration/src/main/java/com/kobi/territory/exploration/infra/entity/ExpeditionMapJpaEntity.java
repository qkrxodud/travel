package com.kobi.territory.exploration.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.map.CountryCode;
import com.kobi.territory.exploration.domain.map.Departure;
import com.kobi.territory.exploration.domain.map.ExpeditionMap;
import com.kobi.territory.exploration.domain.map.InviteCode;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.MapKind;
import com.kobi.territory.exploration.domain.map.MapSettings;
import com.kobi.territory.exploration.domain.map.MapVisibility;
import com.kobi.territory.exploration.domain.map.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** expedition_map 테이블 ↔ ExpeditionMap 루트(멤버는 map_member 자식 행). 변환은 이 엔티티가 가진다. */
@Entity
@Table(name = "expedition_map")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpeditionMapJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "invite_code", nullable = false, unique = true, length = 8)
    private String inviteCode;

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "photo_required", nullable = false)
    private boolean photoRequired;

    @Column(name = "daily_check_in_cap", nullable = false)
    private int dailyCheckInCap;

    @Column(nullable = false, length = 16)
    private String visibility;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 낙관적 락 — 지도 커맨드는 행을 잠글 때 강제 증가한다(멤버 행만 바뀌어도 버전이 오른다, QA P1-1). */
    @Version
    private Long version;

    public static ExpeditionMapJpaEntity from(ExpeditionMap map) {
        ExpeditionMapJpaEntity entity = new ExpeditionMapJpaEntity();
        entity.id = map.id().value();
        entity.apply(map);
        return entity;
    }

    /** 바뀔 수 있는 값(이름·초대코드·소유자·설정)을 도메인 상태로 맞춘다. */
    public void apply(ExpeditionMap map) {
        MapSettings settings = map.settings();
        this.name = map.name();
        this.countryCode = map.country().value();
        this.inviteCode = map.inviteCode().value();
        this.ownerId = map.ownerId().value();
        this.kind = map.kind().name();
        this.photoRequired = settings.photoRequired();
        this.dailyCheckInCap = settings.dailyCheckInCap();
        this.visibility = settings.visibility().name();
        this.createdAt = map.createdAt();
    }

    public String id() {
        return id;
    }

    /** 루트 + 자식(map_member — 현재 멤버와 탈퇴 유예 중) 행으로 애그리거트를 복원한다. */
    public ExpeditionMap toDomain(List<MapMemberJpaEntity> memberRows) {
        List<Member> members = memberRows.stream().filter(row -> !row.departed()).map(MapMemberJpaEntity::toDomain).toList();
        List<Departure> departures = memberRows.stream().filter(MapMemberJpaEntity::departed)
            .map(MapMemberJpaEntity::toDeparture).toList();
        return ExpeditionMap.restore(MapId.of(id), name, new CountryCode(countryCode), new InviteCode(inviteCode),
            ExplorerId.of(ownerId), MapKind.valueOf(kind),
            new MapSettings(photoRequired, dailyCheckInCap, MapVisibility.valueOf(visibility)), createdAt, members, departures);
    }
}
