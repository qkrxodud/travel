package com.kobi.territory.catalog.domain.mystery;

import com.kobi.territory.common.model.Rarity;
import java.util.EnumSet;
import java.util.Set;

/**
 * 이번 주 미스터리 지역 고르기 규칙(mystery.json, 8단계). 후보 = 현행 지역 중 이 희귀도(희귀·전설)인 곳, 그중 방문자 비율이 낮은
 * 쪽 bottomFraction(하위 구간, 최소 1곳)에서 주차로 정해지는 한 곳.
 */
public record MysteryRules(Set<Rarity> rarities, double bottomFraction) {

    public MysteryRules {
        rarities = rarities == null || rarities.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(rarities));
        if (!(bottomFraction > 0 && bottomFraction <= 1)) throw new IllegalStateException("하위 구간 비율은 0 초과 1 이하: " + bottomFraction);
    }

    /** 후보가 없는 규칙(진행 정의가 필요 없는 곳 — 고르기를 요청하면 실패한다). */
    public static MysteryRules none() {
        return new MysteryRules(Set.of(), 1);
    }

    public boolean eligible(Rarity rarity) {
        return rarities.contains(rarity);
    }
}
