package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.model.Rarity;
import java.util.EnumMap;
import java.util.Map;

/**
 * 체크인 보상 값(카탈로그 reward-rules.json → application이 변환해 넘긴다).
 *
 * @param baseXp             희귀도별 지역 기본 XP
 * @param provinceFirstBonus 시·도 첫 발 도장 보너스
 * @param claimBonus         선점 보너스(지도 내 최초 체크인)
 */
public record RewardTable(Map<Rarity, Integer> baseXp, int provinceFirstBonus, int claimBonus) {
    public RewardTable {
        for (Rarity r : Rarity.values()) {
            if (baseXp == null || !baseXp.containsKey(r)) throw new IllegalArgumentException("기본 XP 누락: " + r);
        }
        baseXp = Map.copyOf(new EnumMap<>(baseXp));
    }

    public int base(Rarity rarity) {
        return baseXp.get(rarity);
    }
}
