package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import java.util.Optional;

/** 요청이 가리키는 지도: mapId를 생략(null·공백)하면 요청한 탐험가의 개인 지도다. */
public record MapSelector(MapId mapId) {

    /** 요청한 탐험가의 개인 지도. */
    public static final MapSelector PERSONAL = new MapSelector(null);

    public static MapSelector of(String mapIdOrNull) {
        return mapIdOrNull == null || mapIdOrNull.isBlank() ? PERSONAL : new MapSelector(MapId.of(mapIdOrNull));
    }

    public static MapSelector of(MapId mapId) {
        return new MapSelector(mapId);
    }

    public boolean personal() {
        return mapId == null;
    }

    public Optional<MapId> explicit() {
        return Optional.ofNullable(mapId);
    }

    /** 대상 지도를 찾지 못했을 때의 오류. */
    public ExplorationException notFound() {
        return ExplorationError.MAP_NOT_FOUND.exception(personal() ? "개인 지도" : mapId.value());
    }
}
