package com.kobi.territory.analytics.domain.metrics;

/** 기능 하나의 사용률: 그 기능을 쓴 사람 ÷ 구간 활성 사람. */
public record FeatureUse(String name, int users, Double rate) {}
