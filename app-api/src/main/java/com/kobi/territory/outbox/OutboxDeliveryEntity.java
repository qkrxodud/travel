package com.kobi.territory.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * outbox_delivery — 구독자별 전달 기록(D5). (event_id, subscriber) 한 행. 구독자 처리와 DELIVERED 기록은 같은 트랜잭션이라
 * 성공한 구독자는 다시 받지 않는다.
 * <ul>
 *   <li>낙관적 락 충돌: conflicts+1, 횟수 상한에 세지 않고 nextAttemptAt 까지 미룬다(지수 백오프 + 지터, QA P1-2).
 *       단 첫 충돌(first_conflict_at)부터 시간 상한(기본 10분, 결정 5)을 넘기면 FAILED — 결정적으로 항상 충돌하는 버그가
 *       그 단위를 조용히 영원히 멈추지 않게 재전달 경로로 드러낸다(R2-3).</li>
 *   <li>그 밖의 실패: attempts+1, 상한에 닿으면 FAILED. FAILED 는 그 순서 단위를 멈추고 {@link #redeliver}로만 풀린다.</li>
 * </ul>
 */
@Entity
@Table(name = "outbox_delivery")
@IdClass(OutboxDeliveryEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxDeliveryEntity {

    public enum Status { PENDING, DELIVERED, FAILED }

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Id
    @Column(length = 80)
    private String subscriber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private int conflicts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /** 연속 충돌의 첫 시각(성공·재전달·충돌 아닌 실패에서 비운다). */
    @Column(name = "first_conflict_at")
    private Instant firstConflictAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    OutboxDeliveryEntity(Long eventId, String subscriber, Instant now) {
        this.eventId = eventId;
        this.subscriber = subscriber;
        this.status = Status.PENDING;
        this.updatedAt = now;
    }

    public Long eventId() { return eventId; }
    public String subscriber() { return subscriber; }
    public int attempts() { return attempts; }
    public int conflicts() { return conflicts; }
    public String lastError() { return lastError; }

    public boolean delivered() {
        return status == Status.DELIVERED;
    }

    public boolean failed() {
        return status == Status.FAILED;
    }

    /** 백오프 중이라 아직 보낼 때가 아닌지. */
    boolean waitingAt(Instant now) {
        return nextAttemptAt != null && nextAttemptAt.isAfter(now);
    }

    void markDelivered(Instant at) {
        attempts++;
        status = Status.DELIVERED;
        deliveredAt = at;
        nextAttemptAt = null;
        firstConflictAt = null;
        updatedAt = at;
    }

    /**
     * 낙관적 락 충돌 — 횟수 상한에 세지 않고 delay 뒤에 다시. 첫 충돌부터 retryLimit 이 지났으면 FAILED(결정 5).
     * @return FAILED 가 됐는지
     */
    boolean markConflict(String error, Duration delay, Duration retryLimit, Instant at) {
        conflicts++;
        lastError = trim(error);
        updatedAt = at;
        if (firstConflictAt == null) firstConflictAt = at;
        if (!at.isBefore(firstConflictAt.plus(retryLimit))) {
            status = Status.FAILED;
            nextAttemptAt = null;
            return true;
        }
        nextAttemptAt = at.plus(delay);
        return false;
    }

    /** 실패 1회. 상한에 닿으면 FAILED. @return FAILED 가 됐는지 */
    boolean markFailure(String error, int maxAttempts, Duration delay, Instant at) {
        attempts++;
        firstConflictAt = null;
        lastError = trim(error);
        updatedAt = at;
        if (attempts >= maxAttempts) {
            status = Status.FAILED;
            nextAttemptAt = null;
        } else {
            nextAttemptAt = at.plus(delay);
        }
        return failed();
    }

    /** FAILED 재전달: 처음 상태(PENDING, 시도 0)로 되돌린다. */
    void redeliver(Instant at) {
        status = Status.PENDING;
        attempts = 0;
        conflicts = 0;
        nextAttemptAt = null;
        firstConflictAt = null;
        updatedAt = at;
    }

    private static String trim(String error) {
        return error == null ? null : error.substring(0, Math.min(error.length(), 1000));
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long eventId;
        private String subscriber;
    }
}
