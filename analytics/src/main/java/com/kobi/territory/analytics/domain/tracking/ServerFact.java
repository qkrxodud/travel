package com.kobi.territory.analytics.domain.tracking;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 다른 컨텍스트의 공개 이벤트를 분석 말로 옮긴 사실(적기 전). 탐험가 id 원문은 여기까지만 — 적을 때 해시로 바뀐다.
 *
 * @param name       분석 이벤트 이름(EventDefinitions 의 서버 이벤트)
 * @param explorerId 그 사실의 주인공 탐험가 id
 * @param fields     필드(정의에 있는 것만)
 * @param naturalKey 같은 사실이면 같은 값(재전달 멱등) — 공개 이벤트의 종류와 내용 전체
 */
public record ServerFact(String name, String explorerId, Instant occurredAt, Map<String, Object> fields, String naturalKey) {

    public ServerFact {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(explorerId, "explorerId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(naturalKey, "naturalKey");
        fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
