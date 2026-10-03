package com.kobi.territory.wardrobe.domain.scene;

import com.kobi.territory.common.model.Rarity;
import java.util.EnumMap;
import java.util.Map;

/**
 * 꾸미기 점수 정책: 희귀도별 점수(설정값 territory.wardrobe.style-points.* — 도메인에 숫자를 박지 않는다).
 * StylePoints = 착용 아이템(슬롯 + 장식) 희귀도 점수 합.
 * <p>
 * 희귀도 → 점수 표를 감싸므로 record 가 아니라 class 다(P3-R3-6 — 원본 Map 을 내보내지 않고 {@link #pointsOf}만 공개).
 */
public final class StylePolicy {

    private final Map<Rarity, Integer> pointsByTier;

    public StylePolicy(Map<Rarity, Integer> pointsByTier) {
        EnumMap<Rarity, Integer> copy = new EnumMap<>(Rarity.class);
        copy.putAll(pointsByTier);
        for (Rarity rarity : Rarity.values()) {
            Integer points = copy.get(rarity);
            if (points == null) throw new IllegalArgumentException("꾸미기 점수 누락: " + rarity);
            if (points < 0) throw new IllegalArgumentException("꾸미기 점수는 0 이상: " + rarity + "=" + points);
        }
        this.pointsByTier = copy;
    }

    public int pointsOf(Rarity tier) {
        return pointsByTier.get(tier);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StylePolicy policy && pointsByTier.equals(policy.pointsByTier);
    }

    @Override
    public int hashCode() {
        return pointsByTier.hashCode();
    }

    @Override
    public String toString() {
        return "StylePolicy" + pointsByTier;
    }
}
