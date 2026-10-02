package com.kobi.territory.wardrobe.domain.scene;

import com.kobi.territory.common.model.Rarity;
import java.util.EnumMap;
import java.util.Map;

/**
 * 꾸미기 점수 정책: 희귀도별 점수(설정값 territory.wardrobe.style-points.* — 도메인에 숫자를 박지 않는다).
 * StylePoints = 착용 아이템(슬롯 + 장식) 희귀도 점수 합.
 */
public record StylePolicy(Map<Rarity, Integer> pointsByTier) {
    public StylePolicy {
        pointsByTier = Map.copyOf(new EnumMap<>(pointsByTier));
        for (Rarity rarity : Rarity.values()) {
            if (!pointsByTier.containsKey(rarity)) throw new IllegalArgumentException("꾸미기 점수 누락: " + rarity);
        }
    }

    public int pointsOf(Rarity tier) {
        return pointsByTier.get(tier);
    }
}
