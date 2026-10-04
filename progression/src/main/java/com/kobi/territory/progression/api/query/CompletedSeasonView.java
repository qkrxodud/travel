package com.kobi.territory.progression.api.query;

import java.time.Instant;
import java.util.List;

/**
 * 지도에서 완성된 계절 회차 하나(공개 Query DTO, 9단계) — 꾸미기 재계산이 회차 배경을 완성 시점 멤버(수령자)에게만 복구할 때 쓴다.
 *
 * @param recipientIds 완성 시점 멤버 전원
 */
public record CompletedSeasonView(String roundId, Instant completedAt, List<String> recipientIds) {
    public CompletedSeasonView {
        recipientIds = List.copyOf(recipientIds);
    }
}
