package com.kobi.territory.exploration.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "expedition_map")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ExpeditionMapJpaEntity {

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

    ExpeditionMapJpaEntity(String id, String name, String countryCode, String inviteCode, String ownerId, String kind,
                           boolean photoRequired, int dailyCheckInCap, String visibility, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.countryCode = countryCode;
        this.inviteCode = inviteCode;
        this.ownerId = ownerId;
        this.kind = kind;
        this.photoRequired = photoRequired;
        this.dailyCheckInCap = dailyCheckInCap;
        this.visibility = visibility;
        this.createdAt = createdAt;
    }
}
