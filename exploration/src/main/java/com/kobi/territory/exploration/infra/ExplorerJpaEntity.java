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
@Table(name = "explorer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ExplorerJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    /** 공개 프로필 핸들(4단계). 익명 탐험가는 null. */
    @Column(length = 30, unique = true)
    private String handle;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    ExplorerJpaEntity(String id, String handle, Instant createdAt) {
        this.id = id;
        this.handle = handle;
        this.createdAt = createdAt;
    }
}
