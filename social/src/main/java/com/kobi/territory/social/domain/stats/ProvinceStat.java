package com.kobi.territory.social.domain.stats;

import java.time.Instant;
import java.util.Objects;

/**
 * 시·도 평균 유저(일 1회 배치, province_stats) — 콜드 스타트 비교(§7, 친구 0명이면 "내 지역 평균 유저"). 주 활동 시·도가 이 시·도인
 * 탐험가(지역 1곳 이상)의 수와 지역 수 합 — 평균은 {@link #averageRegionCount()}. {@link #NATIONWIDE} 줄은 지역 1곳 이상인 전체 탐험가.
 */
public record ProvinceStat(String provinceCode, int explorerCount, long regionCountSum, Instant computedAt) {

    /** 전국 평균 줄의 시·도 코드. */
    public static final String NATIONWIDE = "ALL";

    public ProvinceStat {
        Objects.requireNonNull(provinceCode, "provinceCode");
        Objects.requireNonNull(computedAt, "computedAt");
        if (explorerCount < 0 || regionCountSum < 0) throw new IllegalArgumentException("count=" + explorerCount + " sum=" + regionCountSum);
    }

    /** 평균 지역 수(소수 첫째 자리 반올림, 탐험가가 없으면 0). */
    public double averageRegionCount() {
        return explorerCount == 0 ? 0 : Math.round(10.0 * regionCountSum / explorerCount) / 10.0;
    }

    public boolean nationwide() {
        return NATIONWIDE.equals(provinceCode);
    }
}
