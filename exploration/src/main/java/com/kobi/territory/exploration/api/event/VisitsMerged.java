package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;
import java.util.List;

/**
 * 병합(ExplorerMerged)으로 from 의 개인 지도 방문이 explorerId(= into) 의 개인 지도(mapId)로 옮겨졌다. 같은 지역은 방문일이 더 이른
 * 기록이 남는다. RegionVisited 는 따로 나오지 않는다 — 진행·인벤토리는 재계산 예약(recalculation_request)으로 다시 만든다.
 * outbox aggregate = ("Territory", mapId).
 *
 * @param addedRegionCodes    새로 칠해진 지역
 * @param replacedRegionCodes 이미 칠한 지역인데 from 쪽 방문일이 더 일러 그 기록으로 바뀐 지역
 */
public record VisitsMerged(String mapId, String explorerId, String fromExplorerId, List<String> addedRegionCodes,
                           List<String> replacedRegionCodes, Instant mergedAt) implements DomainEvent {
    public VisitsMerged {
        addedRegionCodes = List.copyOf(addedRegionCodes);
        replacedRegionCodes = List.copyOf(replacedRegionCodes);
    }

    @Override
    public Instant occurredAt() {
        return mergedAt;
    }
}
