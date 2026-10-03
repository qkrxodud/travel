package com.kobi.territory.recalc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** recalculation_request 행(V4) — 탐험가당 하나. 같은 탐험가를 다시 예약하면 generation 을 올린다(처리 중인 배치가 지우지 않게). */
@Entity
@Table(name = "recalculation_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RecalculationRequestEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Column(nullable = false, length = 40)
    private String reason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(nullable = false)
    private int generation;

    @Column(nullable = false)
    private int attempts;

    static RecalculationRequestEntity of(String explorerId, String reason, Instant requestedAt) {
        RecalculationRequestEntity entity = new RecalculationRequestEntity();
        entity.explorerId = explorerId;
        entity.reason = reason;
        entity.requestedAt = requestedAt;
        return entity;
    }

    /** 다시 예약됨 — generation 을 올려 처리 중인 배치가 이 행을 지우지 않게 한다. */
    void renew(String nextReason, Instant at) {
        this.reason = nextReason;
        this.requestedAt = at;
        this.generation++;
    }

    String explorerId() { return explorerId; }
    int generation() { return generation; }
}
