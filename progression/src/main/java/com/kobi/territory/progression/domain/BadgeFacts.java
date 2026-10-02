package com.kobi.territory.progression.domain;

import java.util.Map;

/**
 * 뱃지 판정에 쓰는 탐험가 집계 값(ExplorerProgress 가 만든다).
 *
 * @param perProvince    시·도 코드 → 활성 지역 수
 * @param provinceTotals 시·도 코드 → 전체 지역 수(카탈로그)
 */
public record BadgeFacts(int regions, int legend, Map<String, Integer> perProvince, Map<String, Integer> provinceTotals,
                         int totalRegions, int setsCompleted, int streakMonths) {

    public int in(String provinceCode) {
        return perProvince.getOrDefault(provinceCode, 0);
    }

    public boolean complete(String provinceCode) {
        int total = provinceTotals.getOrDefault(provinceCode, 0);
        return total > 0 && in(provinceCode) >= total;
    }
}
