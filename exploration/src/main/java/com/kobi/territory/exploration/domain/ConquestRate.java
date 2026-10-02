package com.kobi.territory.exploration.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 정복률 = 영토 ÷ 전체 지역(전국·시·도별). Territory를 로드한 뒤 메모리에서 계산한다(§5).
 */
public record ConquestRate(int visited, int total, List<ProvinceRate> provinces) {

    /**
     * @param regions        칠해진 지역(중복 없음)
     * @param provinceTotals 시·도 코드 → 전체 지역 수 (표시 순서대로 넣은 LinkedHashMap 권장)
     */
    public static ConquestRate of(Collection<RegionSnapshot> regions, Map<String, Integer> provinceTotals) {
        Map<String, Integer> counts = new HashMap<>();
        for (RegionSnapshot r : regions) {
            if (provinceTotals.containsKey(r.provinceCode())) counts.merge(r.provinceCode(), 1, Integer::sum);
        }
        List<ProvinceRate> rows = new ArrayList<>();
        int total = 0;
        int visited = 0;
        for (var e : provinceTotals.entrySet()) {
            int n = Math.min(counts.getOrDefault(e.getKey(), 0), e.getValue());
            rows.add(new ProvinceRate(e.getKey(), n, e.getValue()));
            total += e.getValue();
            visited += n;
        }
        return new ConquestRate(visited, total, List.copyOf(rows));
    }

    public int percent() {
        return total == 0 ? 0 : (int) Math.round(100.0 * visited / total);
    }

    public record ProvinceRate(String provinceCode, int visited, int total) {
        public int percent() {
            return total == 0 ? 0 : (int) Math.round(100.0 * visited / total);
        }

        public boolean conquered() {
            return total > 0 && visited == total;
        }
    }
}
