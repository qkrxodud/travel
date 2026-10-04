package com.kobi.territory.analytics.domain.metrics;

/** 오류 코드 하나가 화면에 뜬 횟수. */
public record ErrorCount(String code, int count) {}
