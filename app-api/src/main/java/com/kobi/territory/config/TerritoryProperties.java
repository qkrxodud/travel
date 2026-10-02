package com.kobi.territory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "territory")
public record TerritoryProperties(CheckIn checkIn, Map map, ShareCard shareCard) {
    public record CheckIn(int dailyCap, int onboardingGraceHours) {}
    public record Map(int leaveGraceDays) {}
    public record ShareCard(int cacheTtlMinutes) {}
}
