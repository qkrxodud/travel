package com.kobi.territory.notification.domain.delivery;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.domain.policy.NotificationKind;
import java.util.Objects;

/**
 * 발송 기록의 멱등 열쇠 — 탐험가 · 종류 · 기간(미스터리 = 그 주 월요일 {@code 2026-10-05}, 스트릭 = 그 달 {@code 2026-10}, 계절 = 회차
 * {@code autumn-2026}). 같은 열쇠는 한 번만 계획한다(스케줄이 두 번 돌거나 다시 시작해도).
 */
public record DeliveryKey(ExplorerId explorerId, NotificationKind kind, String period) {

    public DeliveryKey {
        Objects.requireNonNull(explorerId, "explorerId");
        Objects.requireNonNull(kind, "kind");
        if (period == null || !period.matches("[a-z0-9-]{1,40}")) throw new IllegalArgumentException("기간 형식: " + period);
    }
}
