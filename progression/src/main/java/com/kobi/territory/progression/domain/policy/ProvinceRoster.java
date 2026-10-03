package com.kobi.territory.progression.domain.policy;

import com.kobi.territory.common.model.RegionCode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 일급 컬렉션: 시·도별 <b>현행</b> 지역 명부(정책 VO, 카탈로그에서 조립 — 8단계). 시·도 100%(정복·"주인" 칭호·시·도 완성 뱃지)는 이
 * 명부의 지역을 모두 칠했는지로 판단한다 — 폐지된 지역(행정구역 개편)은 명부에 없어 칠했어도 세지 않고, 칠하지 않았어도 막지 않는다.
 * 표시 순서(시·도 순서)를 유지한다.
 */
public final class ProvinceRoster {

    private final Map<String, Set<RegionCode>> regionsByProvince;

    private ProvinceRoster(Map<String, Set<RegionCode>> regionsByProvince) {
        Map<String, Set<RegionCode>> copy = new LinkedHashMap<>();
        regionsByProvince.forEach((province, regions) -> copy.put(province, Set.copyOf(regions)));
        this.regionsByProvince = copy;
    }

    /** 시·도 코드(표시 순서) → 현행 지역 코드. */
    public static ProvinceRoster of(Map<String, ? extends Collection<RegionCode>> regionsByProvince) {
        Map<String, Set<RegionCode>> sets = new LinkedHashMap<>();
        regionsByProvince.forEach((province, regions) -> sets.put(province, Set.copyOf(regions)));
        return new ProvinceRoster(sets);
    }

    /** 시·도 코드 → 현행 지역 수(표시 순서). */
    public Map<String, Integer> totals() {
        Map<String, Integer> totals = new LinkedHashMap<>();
        regionsByProvince.forEach((province, regions) -> totals.put(province, regions.size()));
        return totals;
    }

    public int total(String provinceCode) {
        return regionsByProvince.getOrDefault(provinceCode, Set.of()).size();
    }

    /** 이 시·도의 현행 지역 중 활성으로 칠한 곳 수. */
    public int covered(String provinceCode, Set<RegionCode> activeRegions) {
        return (int) regionsByProvince.getOrDefault(provinceCode, Set.of()).stream().filter(activeRegions::contains).count();
    }

    /** 이 시·도의 현행 지역을 모두 칠했는지(현행 지역이 없는 시·도는 정복할 수 없다). */
    public boolean conquered(String provinceCode, Set<RegionCode> activeRegions) {
        int total = total(provinceCode);
        return total > 0 && covered(provinceCode, activeRegions) == total;
    }

    /** 지금 칠한 지역으로 정복한 시·도(표시 순서). */
    public List<String> conqueredBy(Set<RegionCode> activeRegions) {
        return regionsByProvince.keySet().stream().filter(province -> conquered(province, activeRegions)).toList();
    }

    /** 시·도 코드(표시 순서). */
    public List<String> provinces() {
        return List.copyOf(regionsByProvince.keySet());
    }
}
