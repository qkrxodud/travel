package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;
import java.util.List;

/**
 * 탈퇴 처리로 그 멤버의 방문이 지도에서 숨겨졌다(hidden_at — 유예 안 재가입이면 복구). 도감(지도 단위)은
 * regionsGoneFromMap 을 테마 진행에서 뺀다(완성 기록은 유지).
 *
 * @param hiddenRegionCodes  숨긴 방문의 지역
 * @param regionsGoneFromMap 이제 지도에 아무도 칠하지 않은 지역
 */
public record VisitsHidden(String mapId, String explorerId, List<String> hiddenRegionCodes, List<String> regionsGoneFromMap,
                           Instant hiddenAt) implements DomainEvent {
    public VisitsHidden {
        hiddenRegionCodes = List.copyOf(hiddenRegionCodes);
        regionsGoneFromMap = List.copyOf(regionsGoneFromMap);
    }

    @Override
    public Instant occurredAt() {
        return hiddenAt;
    }
}
