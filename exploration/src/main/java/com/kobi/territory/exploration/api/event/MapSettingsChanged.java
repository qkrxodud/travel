package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/** 지도장이 지도 설정(사진 필수·하루 상한·공개 범위)을 바꿨다. @param visibility PUBLIC | FRIENDS | PRIVATE */
public record MapSettingsChanged(String mapId, boolean photoRequired, int dailyCheckInCap, String visibility,
                                 Instant changedAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return changedAt;
    }
}
