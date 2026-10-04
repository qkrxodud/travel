package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;
import java.util.List;

/**
 * 유예 안 재가입으로 숨겼던 방문이 복구됐다(선점은 돌아오지 않는다). 도감은 regionsBackOnMap 을 다시 테마 진행에 넣는다 —
 * 그로 인해 테마가 완성되면 memberIds(복구 시점 멤버)가 수령자다.
 *
 * @param restoredVisits (9단계, 끝에 추가 — 하위 호환) 복구한 방문마다 지역과 원래 처리 시각. 계절 한정 테마는 그 회차 기간 안에 처리된
 *                       방문만 다시 센다. 예전 이벤트는 빈 목록(계절 진행에 다시 넣지 않는다)
 */
public record VisitsRestored(String mapId, String explorerId, List<String> restoredRegionCodes, List<String> regionsBackOnMap,
                             List<String> memberIds, Instant restoredAt, List<RestoredVisit> restoredVisits) implements DomainEvent {
    public VisitsRestored {
        restoredRegionCodes = List.copyOf(restoredRegionCodes);
        regionsBackOnMap = List.copyOf(regionsBackOnMap);
        memberIds = List.copyOf(memberIds);
        restoredVisits = restoredVisits == null ? List.of() : List.copyOf(restoredVisits);
    }

    /** 복구한 방문의 처리 시각을 모르는(8단계 이전 형식) 이벤트. */
    public VisitsRestored(String mapId, String explorerId, List<String> restoredRegionCodes, List<String> regionsBackOnMap,
                          List<String> memberIds, Instant restoredAt) {
        this(mapId, explorerId, restoredRegionCodes, regionsBackOnMap, memberIds, restoredAt, List.of());
    }

    @Override
    public Instant occurredAt() {
        return restoredAt;
    }

    /** 복구한 방문 하나 — 지역과 원래 처리 시각(체크인 시각). */
    public record RestoredVisit(String regionCode, Instant visitedAt) {}
}
