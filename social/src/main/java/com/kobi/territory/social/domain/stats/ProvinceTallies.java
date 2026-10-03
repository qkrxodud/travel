package com.kobi.territory.social.domain.stats;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 일급 컬렉션: 탐험가 × 시·도 활성 지역 수. 탐험가의 지역 수(시·도 합 = 탐험가 단위 중복 제거 지역 수)와 주 활동 시·도(가장 많이 칠한
 * 시·도, 같으면 코드 순)를 낸다 — 친구 랭킹·상위 %·콜드 스타트 비교의 기준.
 */
public final class ProvinceTallies {

    private final List<ProvinceTally> tallies;

    private ProvinceTallies(List<ProvinceTally> tallies) {
        this.tallies = List.copyOf(tallies);
    }

    public static ProvinceTallies of(Collection<ProvinceTally> tallies) {
        return new ProvinceTallies(List.copyOf(tallies));
    }

    /** 탐험가의 지역 수(없으면 0). */
    public int regionCountOf(ExplorerId explorerId) {
        return tallies.stream().filter(tally -> tally.explorerId().equals(explorerId)).mapToInt(ProvinceTally::regionCount).sum();
    }

    /** 주 활동 시·도(지역이 없으면 빈 값). */
    public Optional<String> mainProvinceOf(ExplorerId explorerId) {
        return tallies.stream().filter(tally -> tally.explorerId().equals(explorerId) && tally.regionCount() > 0)
            .min(Comparator.comparingInt(ProvinceTally::regionCount).reversed().thenComparing(ProvinceTally::provinceCode))
            .map(ProvinceTally::provinceCode);
    }

    /** population 중 지역이 1곳 이상인 탐험가 → 지역 수. */
    Map<ExplorerId, Integer> regionCountsAmong(Set<ExplorerId> population) {
        return tallies.stream().filter(tally -> population.contains(tally.explorerId()))
            .collect(Collectors.groupingBy(ProvinceTally::explorerId, Collectors.summingInt(ProvinceTally::regionCount)))
            .entrySet().stream().filter(entry -> entry.getValue() > 0)
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
