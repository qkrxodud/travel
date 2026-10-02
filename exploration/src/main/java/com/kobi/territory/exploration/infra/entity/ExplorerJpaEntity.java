package com.kobi.territory.exploration.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.explorer.AccessTokenHash;
import com.kobi.territory.exploration.domain.explorer.Explorer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** explorer 테이블 ↔ Explorer(계정 루트). 변환은 이 엔티티가 가진다. 토큰은 해시만 저장한다(V3). */
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

    /** 접근 토큰 SHA-256 hex. 3단계 이전 탐험가는 null. */
    @Column(name = "access_token_hash", length = 64, unique = true)
    private String accessTokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ExplorerJpaEntity from(Explorer explorer) {
        ExplorerJpaEntity entity = new ExplorerJpaEntity();
        entity.id = explorer.id().value();
        entity.handle = explorer.handle();
        entity.accessTokenHash = explorer.tokenHash() == null ? null : explorer.tokenHash().value();
        entity.createdAt = explorer.createdAt();
        return entity;
    }

    public ExplorerId explorerId() {
        return ExplorerId.of(id);
    }

    public Explorer toDomain() {
        return Explorer.restore(explorerId(), handle, accessTokenHash == null ? null : new AccessTokenHash(accessTokenHash),
            createdAt);
    }
}
