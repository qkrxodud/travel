package com.kobi.territory.social.domain.stats;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 일 1회 집계 배치의 결과(rank_percentile · region_stats · province_stats 를 통째로 바꾼다). explorer_region(진행)에서 다시 계산할 수 있는
 * 읽기 모델이라 배치를 다시 돌리면 그대로 복구된다. 계산(compute)과 조회(percentileOf)를 가진 결과 묶음이라 class(QA P3-8).
 * <p>
 * <b>모집단 = 활성 지역이 1곳 이상인 활성 탐험가</b>(리더 결정 5 — 병합돼 비활성인 탐험가·지역 0곳 제외). 상위 %·지역별 방문자 비율·전국
 * 평균이 모두 같은 모집단을 쓴다(QA P2-3).
 */
public final class RankSnapshot {

    private final int population;
    private final List<RankPercentile> percentiles;
    private final List<RegionStat> regionStats;
    private final List<ProvinceStat> provinceStats;
    private final List<RegionOverflow> overflows;
    private final Instant computedAt;

    private RankSnapshot(int population, List<RankPercentile> percentiles, List<RegionStat> regionStats,
                         List<ProvinceStat> provinceStats, List<RegionOverflow> overflows, Instant computedAt) {
        this.population = population;
        this.overflows = List.copyOf(overflows);
        this.percentiles = List.copyOf(percentiles);
        this.regionStats = List.copyOf(regionStats);
        this.provinceStats = List.copyOf(provinceStats);
        this.computedAt = Objects.requireNonNull(computedAt, "computedAt");
    }

    /**
     * @param activeExplorers 활성 탐험가 전부(병합돼 비활성인 탐험가의 집계 줄을 거르는 기준)
     * @param tallies         탐험가 × 시·도 활성 지역 수
     * @param regionVisitors  지역 코드 → 그 지역을 활성으로 가진 <b>활성</b> 탐험가 수(호출자가 활성 탐험가로 걸러 센다 — QA P2-4)
     */
    public static RankSnapshot compute(Collection<ExplorerId> activeExplorers, ProvinceTallies tallies,
                                       Map<String, Integer> regionVisitors, Instant at) {
        Set<ExplorerId> active = new LinkedHashSet<>(activeExplorers);
        Map<ExplorerId, Integer> counts = tallies.regionCountsAmong(active);
        int population = counts.size();
        List<RegionOverflow> overflows = new TreeMap<>(regionVisitors).entrySet().stream()
            .filter(entry -> entry.getValue() > population)
            .map(entry -> new RegionOverflow(entry.getKey(), entry.getValue(), population)).toList();
        return new RankSnapshot(population, percentiles(counts, population, at), regionStats(regionVisitors, population, at),
            provinceStats(counts, tallies, at), overflows, at);
    }

    /** 정렬 한 번으로 경쟁 순위(같은 지역 수 = 같은 순위) — O(n log n)(QA P3-7). */
    private static List<RankPercentile> percentiles(Map<ExplorerId, Integer> counts, int population, Instant at) {
        List<Map.Entry<ExplorerId, Integer>> ordered = counts.entrySet().stream()
            .sorted(Map.Entry.<ExplorerId, Integer>comparingByValue().reversed().thenComparing(entry -> entry.getKey().value()))
            .toList();
        List<RankPercentile> out = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            Map.Entry<ExplorerId, Integer> entry = ordered.get(i);
            boolean tied = i > 0 && ordered.get(i - 1).getValue().equals(entry.getValue());
            int rank = tied ? out.get(i - 1).rank() : i + 1;
            out.add(new RankPercentile(entry.getKey(), entry.getValue(), rank, population, RankPercentile.topPercent(rank, population), at));
        }
        return out;
    }

    /** 넘친 지역(방문자 > 모집단)은 그 지역만 모집단으로 맞춘다 — 배치 전체를 멈추지 않는다(QA r2 P3-B, overflows 로 드러낸다). */
    private static List<RegionStat> regionStats(Map<String, Integer> regionVisitors, int population, Instant at) {
        return new TreeMap<>(regionVisitors).entrySet().stream()
            .map(entry -> new RegionStat(entry.getKey(), Math.min(entry.getValue(), population), population, at)).toList();
    }

    private static List<ProvinceStat> provinceStats(Map<ExplorerId, Integer> counts, ProvinceTallies tallies, Instant at) {
        Map<String, List<Integer>> byMainProvince = new TreeMap<>(counts.entrySet().stream().collect(Collectors.groupingBy(
            entry -> tallies.mainProvinceOf(entry.getKey()).orElseThrow(),
            Collectors.mapping(Map.Entry::getValue, Collectors.toList()))));
        List<ProvinceStat> stats = new ArrayList<>();
        byMainProvince.forEach((province, values) -> stats.add(stat(province, values, at)));
        stats.add(stat(ProvinceStat.NATIONWIDE, List.copyOf(counts.values()), at));
        return stats;
    }

    private static ProvinceStat stat(String provinceCode, List<Integer> regionCounts, Instant at) {
        return new ProvinceStat(provinceCode, regionCounts.size(), regionCounts.stream().mapToLong(Integer::longValue).sum(), at);
    }

    /** 이 탐험가의 상위 %(지역 0곳·비활성·배치 뒤 가입이면 빈 값). */
    public Optional<RankPercentile> percentileOf(ExplorerId explorerId) {
        return percentiles.stream().filter(percentile -> percentile.explorerId().equals(explorerId)).findFirst();
    }

    /** 모집단(활성 지역 1곳 이상인 활성 탐험가 수). */
    public int population() { return population; }
    public List<RankPercentile> percentiles() { return percentiles; }
    public List<RegionStat> regionStats() { return regionStats; }
    public List<ProvinceStat> provinceStats() { return provinceStats; }
    public Instant computedAt() { return computedAt; }
    /** 방문자 수가 모집단을 넘어 맞춘 지역(원인 추적용 — 비어 있어야 정상). */
    public List<RegionOverflow> overflows() { return overflows; }

    @Override
    public boolean equals(Object other) {
        return other instanceof RankSnapshot snapshot && population == snapshot.population && percentiles.equals(snapshot.percentiles)
            && regionStats.equals(snapshot.regionStats) && provinceStats.equals(snapshot.provinceStats)
            && computedAt.equals(snapshot.computedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(population, percentiles, regionStats, provinceStats, computedAt);
    }

    @Override
    public String toString() {
        return "RankSnapshot[population=" + population + ", ranked=" + percentiles.size() + ", regions=" + regionStats.size() + "]";
    }
}
