package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;
import java.time.LocalDate;

/** 방문 기록(날짜·메모·사진) 수정. 진행은 구독하지 않는다(visitDate는 진행에 영향 없음). 메모는 싣지 않는다(프라이버시). */
public record VisitEdited(
    String explorerId,
    String mapId,
    String regionCode,
    LocalDate visitDate,
    boolean hasMemo,
    boolean hasPhoto,
    Instant editedAt
) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return editedAt;
    }
}
