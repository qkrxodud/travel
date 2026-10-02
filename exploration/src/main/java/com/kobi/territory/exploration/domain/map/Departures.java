package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 일급 컬렉션: 탈퇴 유예 중인 멤버. 탐험가당 하나. 변경은 ExpeditionMap 만 한다. */
public final class Departures {

    private final List<Departure> items;

    private Departures(Collection<Departure> departures) {
        this.items = new ArrayList<>();
        departures.forEach(this::add);
    }

    public static Departures of(Collection<Departure> departures) {
        return new Departures(departures);
    }

    void add(Departure departure) {
        if (find(departure.explorerId()).isPresent()) throw new IllegalStateException("탈퇴 기록 중복: " + departure.explorerId());
        items.add(departure);
    }

    Optional<Departure> find(ExplorerId explorerId) {
        return items.stream().filter(departure -> departure.explorerId().equals(explorerId)).findFirst();
    }

    void remove(Departure departure) {
        items.remove(departure);
    }

    /** 유예가 끝난 기록을 빼서 돌려준다. */
    List<Departure> removeExpired(Instant now, Duration grace) {
        List<Departure> expired = items.stream().filter(departure -> departure.expired(now, grace)).toList();
        items.removeAll(expired);
        return expired;
    }

    public boolean anyExpired(Instant now, Duration grace) {
        return items.stream().anyMatch(departure -> departure.expired(now, grace));
    }

    public int size() {
        return items.size();
    }

    public List<Departure> asList() {
        return List.copyOf(items);
    }
}
