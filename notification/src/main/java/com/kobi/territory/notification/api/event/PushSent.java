package com.kobi.territory.notification.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 알림 한 건을 한 사람의 기기에 보냈다(한 기기라도 받음, 12단계). 분석이 {@code push_sent} 로 적는다(발송 대비 클릭률). 문구·주소·기기 정보는
 * 싣지 않는다. aggregate = ("PushDelivery", explorerId).
 *
 * @param kind    {@code mystery} | {@code streak} | {@code season}
 * @param period  그 알림의 기간(미스터리 = 월요일 날짜, 스트릭 = 달, 계절 = 회차 id)
 * @param devices 받은 기기 수
 */
public record PushSent(String explorerId, String kind, String period, int devices, Instant sentAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return sentAt;
    }
}
