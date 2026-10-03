package com.kobi.territory.exploration.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.explorer.AccessTokenHash;
import com.kobi.territory.exploration.domain.explorer.Explorer;
import com.kobi.territory.exploration.domain.explorer.ExplorerStatus;
import com.kobi.territory.exploration.domain.explorer.Handle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** explorer 테이블 ↔ Explorer(계정 루트). 변환은 이 엔티티가 가진다. 토큰은 해시만 저장한다(V3). 상태·병합 대상은 V4. */
@Entity
@Table(name = "explorer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExplorerJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    /** 공개 프로필 핸들(4단계, 소문자). 익명 탐험가는 null. */
    @Column(length = 30, unique = true)
    private String handle;

    /** 접근 토큰 SHA-256 hex. 3단계 이전 탐험가·계정 연결·병합된 탐험가는 null. */
    @Column(name = "access_token_hash", length = 64, unique = true)
    private String accessTokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "merged_into", length = 36)
    private String mergedInto;

    @Column(name = "merged_at")
    private Instant mergedAt;

    public static ExplorerJpaEntity from(Explorer explorer) {
        ExplorerJpaEntity entity = new ExplorerJpaEntity();
        entity.id = explorer.id().value();
        entity.createdAt = explorer.createdAt();
        entity.apply(explorer);
        return entity;
    }

    /** 바뀔 수 있는 값(handle·토큰·상태)을 반영한다. */
    public void apply(Explorer explorer) {
        this.handle = explorer.handle() == null ? null : explorer.handle().value();
        this.accessTokenHash = explorer.tokenHash() == null ? null : explorer.tokenHash().value();
        this.status = explorer.status().name();
        this.mergedInto = explorer.mergedInto().map(ExplorerId::value).orElse(null);
        this.mergedAt = explorer.mergedAt();
    }

    public ExplorerId explorerId() {
        return ExplorerId.of(id);
    }

    /** @param account 자식 account 행(없으면 null), @param reservations 자식 handle_reservation 행 */
    public Explorer toDomain(AccountJpaEntity account, List<HandleReservationJpaEntity> reservations) {
        return Explorer.restore(explorerId(), handle == null ? null : new Handle(handle),
            accessTokenHash == null ? null : new AccessTokenHash(accessTokenHash), createdAt,
            account == null ? null : account.toDomain(), ExplorerStatus.valueOf(status),
            mergedInto == null ? null : ExplorerId.of(mergedInto), mergedAt,
            reservations.stream().map(HandleReservationJpaEntity::toDomain).toList());
    }
}
