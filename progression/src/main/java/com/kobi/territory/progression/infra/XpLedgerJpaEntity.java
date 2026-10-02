package com.kobi.territory.progression.infra;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.domain.XpLedgerEntry;
import com.kobi.territory.progression.domain.XpSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** xp_ledger — 장부 한 줄(추가만). ref_id UNIQUE 가 멱등 키. */
@Entity
@Table(name = "xp_ledger")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class XpLedgerJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "explorer_id", nullable = false, length = 36)
    private String explorerId;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(nullable = false)
    private int amount;

    @Column(name = "ref_id", nullable = false, unique = true, length = 160)
    private String refId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static XpLedgerJpaEntity from(ExplorerId explorer, XpLedgerEntry entry) {
        XpLedgerJpaEntity entity = new XpLedgerJpaEntity();
        entity.explorerId = explorer.value();
        entity.source = entry.source().name();
        entity.amount = entry.amount();
        entity.refId = entry.refId();
        entity.createdAt = entry.at();
        return entity;
    }

    String refId() {
        return refId;
    }

    XpLedgerEntry toDomain() {
        return new XpLedgerEntry(XpSource.valueOf(source), amount, refId, createdAt);
    }
}
