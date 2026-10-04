package com.kobi.territory.notification.domain.delivery;

import java.time.LocalDate;
import java.util.List;

/**
 * 일급 컬렉션: 한 사람의 그날(서울 날짜) 발송 기록들과, 같은 열쇠의 기록이 이미 있는지. 하루 최대 개수 판단의 근거라 루트 잠금 뒤에 읽는다.
 */
public final class ScheduledDeliveries {

    private final LocalDate day;
    private final List<PushDelivery> onDay;
    private final boolean keyTaken;

    private ScheduledDeliveries(LocalDate day, List<PushDelivery> onDay, boolean keyTaken) {
        this.day = day;
        this.onDay = List.copyOf(onDay);
        this.keyTaken = keyTaken;
        if (this.onDay.stream().anyMatch(delivery -> !delivery.deliveryDay().equals(day))) {
            throw new IllegalArgumentException("다른 날의 기록이 섞였다");
        }
    }

    /**
     * @param onDay    그날 보내기로 한 기록들
     * @param keyTaken 같은 열쇠(탐험가·종류·기간)의 기록이 날짜와 상관없이 이미 있는지
     */
    public static ScheduledDeliveries of(LocalDate day, List<PushDelivery> onDay, boolean keyTaken) {
        return new ScheduledDeliveries(day, onDay, keyTaken);
    }

    public LocalDate day() {
        return day;
    }

    public boolean keyTaken() {
        return keyTaken;
    }

    /** 그날 받았거나 받을 알림 수(만료·실패·취소는 세지 않는다). */
    public int activeCount() {
        return (int) onDay.stream().filter(delivery -> delivery.status().countsTowardDailyLimit()).count();
    }
}
