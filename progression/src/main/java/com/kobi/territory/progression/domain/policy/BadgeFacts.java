package com.kobi.territory.progression.domain.policy;

import java.util.Map;
import java.util.Set;

/**
 * 뱃지 판정에 쓰는 탐험가 집계 값(ExplorerProgress 가 만든다).
 *
 * @param perProvince         시·도 코드 → 활성 지역 수
 * @param provinceTotals      시·도 코드 → 현행 지역 수(카탈로그)
 * @param conqueredProvinces  현행 지역을 모두 칠한 시·도(폐지 지역 제외 — 8단계)
 * @param mysteryFound        이번 주 미스터리 보너스를 받은 주 수(8단계)
 * @param revisitStamps       재방문 도장 수(9단계)
 * @param wishesFulfilled     다녀온 가고 싶은 곳 수(9단계)
 */
public record BadgeFacts(int regions, int legend, Map<String, Integer> perProvince, Map<String, Integer> provinceTotals,
                         Set<String> conqueredProvinces, int totalRegions, int themesCompleted, int streakMonths,
                         int mysteryFound, int revisitStamps, int wishesFulfilled) {

    /** 컬렉션은 방어 복사(null 불가). */
    public BadgeFacts {
        perProvince = Map.copyOf(perProvince);
        provinceTotals = Map.copyOf(provinceTotals);
        conqueredProvinces = Set.copyOf(conqueredProvinces);
    }

    public int in(String provinceCode) {
        return perProvince.getOrDefault(provinceCode, 0);
    }

    /** 이 시·도의 현행 지역을 모두 칠했는지. */
    public boolean complete(String provinceCode) {
        return conqueredProvinces.contains(provinceCode);
    }
}
