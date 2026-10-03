package com.kobi.territory.progression.api.query;

/** 탐험가 × 시·도 → 활성 지역 수(공개 Query DTO, 5단계). */
public record ProvinceTallyView(String explorerId, String provinceCode, int regionCount) {}
