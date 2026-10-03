package com.kobi.territory.exploration.domain.explorer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 일급 컬렉션: 한 탐험가가 놓은 handle 의 예약(handle 당 하나). 변경은 Explorer 만 한다. */
public final class HandleReservations {

    private final List<HandleReservation> items;

    private HandleReservations(Collection<HandleReservation> reservations) {
        this.items = new ArrayList<>(reservations);
    }

    public static HandleReservations of(Collection<HandleReservation> reservations) {
        return new HandleReservations(reservations);
    }

    /** 놓은 handle 을 예약하고(같은 handle 은 새 기한으로), 기한이 끝난 예약은 지운다. 다시 쓰는 handle 은 예약에서 뺀다. */
    void release(Handle released, Handle taken, Instant at, Duration period) {
        items.removeIf(reservation -> !reservation.activeAt(at) || reservation.handle().equals(released)
            || reservation.handle().equals(taken));
        if (released != null) items.add(new HandleReservation(released, at.plus(period)));
    }

    public List<HandleReservation> asList() {
        return List.copyOf(items);
    }
}
