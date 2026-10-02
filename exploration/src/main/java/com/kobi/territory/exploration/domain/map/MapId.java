package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.exploration.domain.ExplorationError;
import java.util.Objects;
import java.util.UUID;

/** 지도(ExpeditionMap) 식별자 = Territory 키. */
public record MapId(String value) {
    public MapId {
        Objects.requireNonNull(value, "mapId");
        try {
            value = UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw ExplorationError.MAP_NOT_FOUND.exception(value);
        }
    }

    public static MapId newId() {
        return new MapId(UUID.randomUUID().toString());
    }

    public static MapId of(String value) {
        return new MapId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
