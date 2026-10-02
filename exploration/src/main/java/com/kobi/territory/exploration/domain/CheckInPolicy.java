package com.kobi.territory.exploration.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * 체크인 규칙 값. domain은 설정을 모르므로 application이 지도 설정(MapSettings.dailyCheckInCap)과
 * territory.check-in.onboarding-grace-hours 값으로 만들어 넘긴다. 숫자를 여기 박지 않는다.
 *
 * @param dailyCap        지도별·멤버별 하루 체크인 상한
 * @param onboardingGrace 지도 가입 후 상한 미적용 기간
 * @param photoRequired   사진 없는 체크인 거부 여부
 */
public record CheckInPolicy(int dailyCap, Duration onboardingGrace, boolean photoRequired) {
    public CheckInPolicy {
        if (dailyCap < 1) throw new IllegalArgumentException("dailyCap >= 1: " + dailyCap);
        Objects.requireNonNull(onboardingGrace, "onboardingGrace");
        if (onboardingGrace.isNegative()) throw new IllegalArgumentException("onboardingGrace >= 0");
    }

    /** 개발용 시드처럼 상한을 적용하지 않는 정책. */
    public static CheckInPolicy unlimited() {
        return new CheckInPolicy(Integer.MAX_VALUE, Duration.ZERO, false);
    }
}
