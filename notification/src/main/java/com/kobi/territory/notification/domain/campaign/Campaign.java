package com.kobi.territory.notification.domain.campaign;

import com.kobi.territory.notification.domain.policy.NotificationKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 한 번의 알림 보내기 — 종류·기간(멱등 열쇠의 기간)과 보낼 시각. 보낼 시각은 조용한 시간을 피한 시각이고, 보낼 날은 그 시각의 서울 날짜다.
 *
 * @param period      미스터리 = 그 주 월요일, 스트릭 = 그 달, 계절 = 회차 id
 * @param deliveryDay 보낼 날(하루 최대 개수의 "하루")
 * @param immediate   local 즉시 발송 — 조용한 시간에도 보낸다(날짜 안에서만, 동의·설정·하루 최대 개수·멱등은 그대로)
 */
public record Campaign(NotificationKind kind, String period, LocalDate deliveryDay, Instant dueAt, boolean immediate) {

    public Campaign {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(deliveryDay, "deliveryDay");
        Objects.requireNonNull(dueAt, "dueAt");
    }
}
