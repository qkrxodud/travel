package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;
import java.util.List;

/**
 * 유예 안 재가입으로 숨겼던 방문이 복구됐다(선점은 돌아오지 않는다). 도감은 regionsBackOnMap 을 다시 테마 진행에 넣는다 —
 * 그로 인해 테마가 완성되면 memberIds(복구 시점 멤버)가 수령자다.
 */
public record VisitsRestored(String mapId, String explorerId, List<String> restoredRegionCodes, List<String> regionsBackOnMap,
                             List<String> memberIds, Instant restoredAt) implements DomainEvent {
    public VisitsRestored {
        restoredRegionCodes = List.copyOf(restoredRegionCodes);
        regionsBackOnMap = List.copyOf(regionsBackOnMap);
        memberIds = List.copyOf(memberIds);
    }

    @Override
    public Instant occurredAt() {
        return restoredAt;
    }
}
