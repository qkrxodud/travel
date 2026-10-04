package com.kobi.territory.analytics.domain.tracking;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/** 검증·정규화를 마친 이벤트 필드(키 순서 고정). 정의에 있는 필드만 들어온다. */
public final class EventProperties {

    private static final EventProperties NONE = new EventProperties(new TreeMap<>());

    private final SortedMap<String, String> values;

    private EventProperties(SortedMap<String, String> values) {
        this.values = values;
    }

    public static EventProperties none() {
        return NONE;
    }

    static EventProperties of(Map<String, String> values) {
        return values.isEmpty() ? NONE : new EventProperties(new TreeMap<>(values));
    }

    public Optional<String> value(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** 저장용 읽기 전용 보기(키 순서 고정). */
    public SortedMap<String, String> view() {
        return Collections.unmodifiableSortedMap(values);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EventProperties properties && values.equals(properties.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
