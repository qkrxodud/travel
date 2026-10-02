package com.kobi.territory.exploration.application;

import java.time.Duration;

/**
 * 탐험 컨텍스트가 쓰는 게임 규칙 값. app-api의 TerritoryProperties(territory.check-in.*)가 바인딩하고
 * app-api 설정이 이 record 빈으로 변환해 넣는다(모듈은 app-api를 모른다).
 *
 * @param defaultDailyCap      새 지도 MapSettings.dailyCheckInCap 기본값
 * @param onboardingGraceHours 지도 가입 후 하루 상한 미적용 시간
 */
public record ExplorationSettings(int defaultDailyCap, int onboardingGraceHours) {
    public ExplorationSettings {
        if (defaultDailyCap < 1) throw new IllegalArgumentException("territory.check-in.daily-cap >= 1");
        if (onboardingGraceHours < 0) throw new IllegalArgumentException("territory.check-in.onboarding-grace-hours >= 0");
    }

    public Duration onboardingGrace() {
        return Duration.ofHours(onboardingGraceHours);
    }
}
