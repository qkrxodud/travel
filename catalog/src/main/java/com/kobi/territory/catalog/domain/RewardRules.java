package com.kobi.territory.catalog.domain;

import com.kobi.territory.common.model.Rarity;
import java.util.EnumMap;
import java.util.Map;

/**
 * 체크인 보상 규칙 값(프로토타입 XP/BONUS 상수). 배포 단위로 바뀌는 참조 데이터라 리소스 JSON에 둔다.
 * 2단계(진행)에서 XP 공식이 확정되면 이 값을 공유한다.
 */
public record RewardRules(Map<Rarity, Integer> xpByRarity, int provinceFirstBonus, int setCompleteBonus, int claimBonus) {
    public RewardRules {
        for (Rarity r : Rarity.values()) {
            if (xpByRarity == null || !xpByRarity.containsKey(r)) {
                throw new IllegalArgumentException("희귀도별 XP 누락: " + r);
            }
        }
        xpByRarity = Map.copyOf(new EnumMap<>(xpByRarity));
    }
}
