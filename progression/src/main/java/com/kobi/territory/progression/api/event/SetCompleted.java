package com.kobi.territory.progression.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;
import java.util.List;

/**
 * 도감 테마(세트) 완성(지도 단위, 단 한 번). 3단계(리더 결정 1)부터 완성 시점 지도 멤버 <b>전원</b>이 수령자이고,
 * 수령자마다 한 건씩 발행한다 — 진행(보너스 XP·칭호)과 꾸미기(세트 배경)는 explorerId 한 명만 처리하면 된다.
 *
 * @param explorerId   이 이벤트의 수령자
 * @param completedBy  마지막 지역을 칠해 완성시킨 탐험가. null = 2단계 이벤트(explorerId 가 완성자이자 유일한 수령자)
 * @param recipientIds 완성 시점 멤버 전원(같은 완성의 모든 이벤트에 같은 값). null = 2단계 이벤트
 */
public record SetCompleted(String mapId, String setId, String explorerId, Instant completedAt, String completedBy,
                           List<String> recipientIds) implements DomainEvent {
    public SetCompleted {
        recipientIds = recipientIds == null ? null : List.copyOf(recipientIds);
    }

    @Override
    public Instant occurredAt() {
        return completedAt;
    }
}
