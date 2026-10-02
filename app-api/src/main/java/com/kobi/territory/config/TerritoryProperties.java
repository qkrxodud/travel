package com.kobi.territory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "territory")
public record TerritoryProperties(CheckIn checkIn, ExpeditionMapProperties map, ShareCard shareCard) {
    public record CheckIn(int dailyCap, int onboardingGraceHours) {}
    /** territory.map.* — 지도 설정(컴포넌트 이름 map 이 바인딩 키). JDK Map 과 겹치지 않는 이름. */
    public record ExpeditionMapProperties(int leaveGraceDays) {}
    public record ShareCard(int cacheTtlMinutes) {}
}
