package com.kobi.territory.progression.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;
import java.util.List;

/**
 * 계절 한정 테마 회차 완성(9단계, 지도 단위 — 회차당 한 번). 테마 완성(SetCompleted)과 같이 완성 시점 지도 멤버 전원이 수령자이고
 * 수령자마다 한 건씩 발행한다 — 진행(+XP·칭호 season-{계절})·꾸미기(회차 배경)·소셜(소식)은 explorerId 한 명만 처리한다.
 * outbox aggregate = ("Collection", mapId).
 *
 * @param roundId      회차 id({계절}-{연도}, 예 autumn-2026)
 * @param seasonId     계절 id(spring·autumn)
 * @param explorerId   이 이벤트의 수령자
 * @param completedBy  마지막 지역을 칠해(또는 복구해) 완성시킨 탐험가
 * @param recipientIds 완성 시점 멤버 전원
 */
public record SeasonCompleted(String mapId, String roundId, String seasonId, String explorerId, Instant completedAt,
                              String completedBy, List<String> recipientIds) implements DomainEvent {
    public SeasonCompleted {
        recipientIds = List.copyOf(recipientIds);
    }

    @Override
    public Instant occurredAt() {
        return completedAt;
    }
}
