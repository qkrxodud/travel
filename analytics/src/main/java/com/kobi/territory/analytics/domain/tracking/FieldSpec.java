package com.kobi.territory.analytics.domain.tracking;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 이벤트 필드 하나의 규칙.
 *
 * @param allowed 비어 있지 않으면 이 값들만 받는다(정해진 목록 — 예: 탭 이름)
 */
public record FieldSpec(String key, FieldType type, Set<String> allowed, boolean required) {

    public FieldSpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        allowed = Set.copyOf(allowed);
    }

    public static FieldSpec required(String key, FieldType type) {
        return new FieldSpec(key, type, Set.of(), true);
    }

    public static FieldSpec optional(String key, FieldType type) {
        return new FieldSpec(key, type, Set.of(), false);
    }

    public static FieldSpec requiredOneOf(String key, String... values) {
        return new FieldSpec(key, FieldType.TOKEN, Set.of(values), true);
    }

    public static FieldSpec optionalOneOf(String key, String... values) {
        return new FieldSpec(key, FieldType.TOKEN, Set.of(values), false);
    }

    Optional<String> normalize(Object raw) {
        return type.normalize(raw).filter(value -> allowed.isEmpty() || allowed.contains(value));
    }
}
