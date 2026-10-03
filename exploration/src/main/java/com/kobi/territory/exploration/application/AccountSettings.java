package com.kobi.territory.exploration.application;

import java.time.Duration;

/**
 * 계정 규칙 값(4단계). app-api 가 설정값 territory.account.* 를 이 record 빈으로 넘긴다.
 *
 * @param handleReservationDays 바꾸기 전 handle 을 다른 탐험가가 못 가져가게 예약하는 기간(QA P3-10, 기본 30일)
 */
public record AccountSettings(int handleReservationDays) {
    public AccountSettings {
        if (handleReservationDays < 0) throw new IllegalArgumentException("territory.account.handle-reservation-days >= 0");
    }

    public Duration handleReservation() {
        return Duration.ofDays(handleReservationDays);
    }
}
