package com.kobi.territory.exploration.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.explorer.Explorer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** explorer 테이블 ↔ Explorer(계정 루트). 변환은 이 엔티티가 가진다. */
@Entity
@Table(name = "explorer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExplorerJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    /** 공개 프로필 핸들(4단계). 익명 탐험가는 null. */
    @Column(length = 30, unique = true)
    private String handle;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private ExplorerJpaEntity(String id, String handle, Instant createdAt) {
        this.id = id;
        this.handle = handle;
        this.createdAt = createdAt;
    }

    public static ExplorerJpaEntity from(Explorer explorer) {
        return new ExplorerJpaEntity(explorer.id().value(), explorer.handle(), explorer.createdAt());
    }

    public ExplorerId explorerId() {
        return ExplorerId.of(id);
    }

    public Explorer toDomain() {
        return Explorer.restore(explorerId(), handle, createdAt);
    }
}
