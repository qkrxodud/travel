package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 가고 싶은 곳(위시리스트 핀)을 다녀왔다(9단계) — 핀을 꽂은 뒤 그 지역을 어느 지도에서든 칠했다. 핀은 "다녀옴"으로 바뀐다.
 * 하류: 진행(+XP 지역당 한 번 — 핀을 뺐다 다시 꽂아도 한 번, 뱃지 "꿈을 이룬 여행자"). 위시리스트는 비공개라 친구 소식은 없다.
 * outbox aggregate = ("Wishlist", explorerId).
 */
public record WishFulfilled(String explorerId, String regionCode, Instant pinnedAt, Instant fulfilledAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return fulfilledAt;
    }
}
