package com.kobi.territory.exploration.infra.entity;

import com.kobi.territory.exploration.domain.explorer.Handle;
import com.kobi.territory.exploration.domain.explorer.HandleReservation;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** handle_reservation 행(V4) ↔ HandleReservation(Explorer 애그리거트의 자식 — 놓은 handle 의 예약). */
@Entity
@Table(name = "handle_reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HandleReservationJpaEntity {

    @EmbeddedId
    private Key key;

    @Column(name = "reserved_until", nullable = false)
    private Instant reservedUntil;

    public static HandleReservationJpaEntity from(String explorerId, HandleReservation reservation) {
        HandleReservationJpaEntity entity = new HandleReservationJpaEntity();
        entity.key = new Key(explorerId, reservation.handle().value());
        entity.reservedUntil = reservation.reservedUntil();
        return entity;
    }

    public static Key keyOf(String explorerId, HandleReservation reservation) {
        return new Key(explorerId, reservation.handle().value());
    }

    public Key key() {
        return key;
    }

    public void apply(HandleReservation reservation) {
        this.reservedUntil = reservation.reservedUntil();
    }

    public HandleReservation toDomain() {
        return new HandleReservation(new Handle(key.handle), reservedUntil);
    }

    @Embeddable
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "explorer_id", length = 36)
        private String explorerId;

        @Column(length = 30)
        private String handle;

        Key(String explorerId, String handle) {
            this.explorerId = explorerId;
            this.handle = handle;
        }
    }
}
