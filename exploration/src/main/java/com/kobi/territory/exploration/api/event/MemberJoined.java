package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 지도 합류(초대코드). 꾸미기가 이미 완성된 테마 보상(세트 배경)을 새 멤버에게 지급한다(XP·칭호 없음).
 *
 * @param role     OWNER | MEMBER
 * @param rejoined 탈퇴 유예 안에 돌아온 재가입 — 숨긴 방문은 탐험이 복구하고 VisitsRestored 를 낸다(선점은 돌아오지 않음)
 */
public record MemberJoined(String mapId, String explorerId, String role, Instant joinedAt, boolean rejoined)
    implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return joinedAt;
    }
}
