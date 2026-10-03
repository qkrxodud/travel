package com.kobi.territory.catalog.domain.mystery;

import com.kobi.territory.catalog.domain.region.Region;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * 미스터리 지역 고르기(도메인 서비스, 순수 함수). 후보 = 현행 지역 중 규칙의 희귀도인 곳을 방문자 비율 오름차순으로 세운 뒤 아래쪽
 * bottomFraction(올림, 최소 1곳) — "덜 알려진 희귀한 곳". 같은 비율끼리는 지역 코드가 아니라 그 주의 섞기 수(서버 비밀값 + 주차 + 지역)
 * 순으로 세운다 — 통계가 없거나 0인 지역이 많아도 주마다 전국에서 고르게 나온다(QA P3-1). 그 안에서 주차 시드로 한 곳을 고른다.
 * 같은 입력(주·비밀값·통계)이면 언제 어디서 돌려도 같은 답이고, 한 번 고른 결과는 mystery_week 에 기록돼 그 주 내내 그대로다.
 */
public final class MysteryDraw {

    private MysteryDraw() {}

    public static MysteryWeek draw(LocalDate weekStart, List<Region> currentRegions, VisitorShares shares, MysteryRules rules,
                                   MysterySeed seed, Instant at) {
        List<Region> candidates = candidates(weekStart, currentRegions, shares, rules, seed);
        if (candidates.isEmpty()) throw new IllegalStateException("미스터리 지역 후보가 없습니다 — mystery.json 희귀도를 확인하세요");
        int index = (int) Math.floorMod(seed.of(weekStart), (long) candidates.size());
        return new MysteryWeek(weekStart, candidates.get(index).code(), at);
    }

    /** 그 주의 하위 구간 후보(방문자 비율 오름차순, 같으면 그 주의 섞기 수 순). */
    public static List<Region> candidates(LocalDate weekStart, List<Region> currentRegions, VisitorShares shares,
                                          MysteryRules rules, MysterySeed seed) {
        List<Region> eligible = currentRegions.stream().filter(Region::active).filter(region -> rules.eligible(region.rarity()))
            .sorted(Comparator.comparingDouble((Region region) -> shares.ratioOf(region.code()))
                .thenComparingLong(region -> seed.of(weekStart, region.code()))
                .thenComparing(region -> region.code().value()))
            .toList();
        if (eligible.isEmpty()) return List.of();
        int size = Math.max(1, (int) Math.ceil(eligible.size() * rules.bottomFraction()));
        return eligible.subList(0, size);
    }
}
