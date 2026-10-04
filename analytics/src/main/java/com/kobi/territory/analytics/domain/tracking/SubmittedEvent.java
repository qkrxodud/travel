package com.kobi.territory.analytics.domain.tracking;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 화면이 보낸 이벤트 하나(검증 전).
 *
 * @param at     화면이 잰 발생 시각(없으면 null — 서버가 받은 시각으로)
 * @param fields 필드 원문(JSON 값: 문자열·수·참거짓, null 값은 없는 것으로 본다)
 */
public record SubmittedEvent(String name, Instant at, Map<String, Object> fields) {

    public SubmittedEvent {
        fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
