package com.kobi.territory.sharing.domain.showcase;

import java.util.List;

/**
 * 연간 리캡(방문일 기준 그 해 — 월 단위 집계만).
 *
 * @param newRegions    그 해에 방문일이 있는 영토 수
 * @param monthCounts   1~12월 영토 수(12칸)
 * @param topProvince   가장 많이 간 시·도 "서울 3곳", 없으면 null
 * @param rarest        가장 희귀한 곳 "울릉 (전설)", 없으면 null
 * @param newProvinces  그 해에 처음 밟은 시·도 수(그 시·도의 방문이 모두 그 해)
 * @param busiestMonth  가장 바쁜 달 "10월 4곳", 없으면 null
 */
public record YearRecap(int year, int newRegions, List<Integer> monthCounts, String topProvince, String rarest,
                        int newProvinces, String busiestMonth) {
    public YearRecap {
        monthCounts = List.copyOf(monthCounts);
        if (monthCounts.size() != 12) throw new IllegalArgumentException("monthCounts 는 12칸");
    }

    public int maxMonthCount() {
        return monthCounts.stream().mapToInt(Integer::intValue).max().orElse(0);
    }
}
