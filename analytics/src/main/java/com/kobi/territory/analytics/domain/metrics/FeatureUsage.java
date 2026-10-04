package com.kobi.territory.analytics.domain.metrics;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 기능별 사용률 — 구간 안 활성 사람 중 그 기능 이벤트가 하나라도 있는 사람의 비율. 쓴 사람이 없는 기능도 0으로 보인다. */
public final class FeatureUsage {

    private final DayRange range;
    private final int activeUsers;
    private final Map<String, Integer> usersByFeature;

    private FeatureUsage(DayRange range, int activeUsers, Map<String, Integer> usersByFeature) {
        this.range = Objects.requireNonNull(range, "range");
        this.activeUsers = activeUsers;
        this.usersByFeature = usersByFeature;
    }

    /** features 순서대로, 센 값이 없으면 0. */
    public static FeatureUsage of(DayRange range, int activeUsers, List<String> features, Map<String, Integer> counted) {
        Map<String, Integer> users = new LinkedHashMap<>();
        features.forEach(feature -> users.put(feature, counted.getOrDefault(feature, 0)));
        return new FeatureUsage(range, activeUsers, users);
    }

    public DayRange range() {
        return range;
    }

    public int activeUsers() {
        return activeUsers;
    }

    /** 많이 쓴 기능 먼저(같으면 정의 순서). */
    public List<FeatureUse> ranked() {
        return usersByFeature.entrySet().stream()
            .map(entry -> new FeatureUse(entry.getKey(), entry.getValue(), Ratio.of(entry.getValue(), activeUsers)))
            .sorted(Comparator.comparingInt(FeatureUse::users).reversed())
            .toList();
    }

    public int usersOf(String feature) {
        return usersByFeature.getOrDefault(feature, 0);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FeatureUsage usage && range.equals(usage.range) && activeUsers == usage.activeUsers
            && usersByFeature.equals(usage.usersByFeature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, activeUsers, usersByFeature);
    }
}
