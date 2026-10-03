package com.kobi.territory.exploration.domain.explorer;

import java.time.Instant;
import java.util.Objects;

/**
 * 바꾸기 전 handle 의 예약(QA P3-10) — 이미 공유된 {@code /u/옛handle} 링크가 바로 남의 프로필이 되지 않게, 놓은 handle 을
 * reservedUntil 까지 다른 탐험가가 가져갈 수 없다(본인은 되돌릴 수 있다).
 */
public record HandleReservation(Handle handle, Instant reservedUntil) {
    public HandleReservation {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(reservedUntil, "reservedUntil");
    }

    public boolean activeAt(Instant now) {
        return now.isBefore(reservedUntil);
    }
}
