package com.kobi.territory.social.domain.stats;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 일급 컬렉션: 시·도 평균 유저 스냅숏. 콜드 스타트 비교 대상을 고른다 — 내 주 활동 시·도의 평균, 그 시·도 줄이 없거나(배치 전·나 혼자
 * 아님) 내 지역이 없으면 전국 평균.
 */
public final class ProvinceStats {

    private final List<ProvinceStat> stats;

    private ProvinceStats(List<ProvinceStat> stats) {
        this.stats = List.copyOf(stats);
    }

    public static ProvinceStats of(Collection<ProvinceStat> stats) {
        return new ProvinceStats(List.copyOf(stats));
    }

    /**
     * 콜드 스타트 비교 대상(§7): 친구가 1명이라도 있으면 없음(친구와 비교), 친구가 없으면 내 지역 평균 유저.
     *
     * @param friendCount  서로 팔로우한 친구 수
     * @param mainProvince 내 주 활동 시·도(지역이 없으면 빈 값)
     */
    public Optional<ProvinceStat> coldStartBaseline(int friendCount, Optional<String> mainProvince) {
        return friendCount > 0 ? Optional.empty() : baselineFor(mainProvince);
    }

    /** @param mainProvince 내 주 활동 시·도(지역이 없으면 빈 값) */
    public Optional<ProvinceStat> baselineFor(Optional<String> mainProvince) {
        Optional<ProvinceStat> regional = mainProvince.flatMap(code -> find(code)).filter(stat -> stat.explorerCount() > 0);
        return regional.or(() -> find(ProvinceStat.NATIONWIDE).filter(stat -> stat.explorerCount() > 0));
    }

    private Optional<ProvinceStat> find(String provinceCode) {
        return stats.stream().filter(stat -> stat.provinceCode().equals(provinceCode)).findFirst();
    }

    public boolean isEmpty() {
        return stats.isEmpty();
    }
}
