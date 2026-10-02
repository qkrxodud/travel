package com.kobi.territory.catalog.domain;

import com.kobi.territory.common.model.Rarity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 체크인 보상 규칙(프로토타입 XP/BONUS 상수 + 설계 §5 선점 보너스)과 그 순수 계산 함수.
 * 배포 단위로 바뀌는 참조 데이터라 리소스 JSON(reward-rules.json)에 값을 둔다.
 * <p>
 * 2단계 리더 결정 D1: 이 계산 함수 하나를 탐험(체크인 미리보기)과 진행(실제 지급)이 함께 쓴다.
 * 입력은 사실 값(희귀도·시·도 첫 방문·선점)뿐이고 영토(Territory)를 모른다.
 */
public record RewardRules(Map<Rarity, Integer> xpByRarity, int provinceFirstBonus, int setCompleteBonus, int claimBonus) {
    public RewardRules {
        for (Rarity rarity : Rarity.values()) {
            if (xpByRarity == null || !xpByRarity.containsKey(rarity)) {
                throw new IllegalArgumentException("희귀도별 XP 누락: " + rarity);
            }
        }
        xpByRarity = Map.copyOf(new EnumMap<>(xpByRarity));
    }

    /** 희귀도별 지역 기본 XP. */
    public int base(Rarity rarity) {
        return xpByRarity.get(Objects.requireNonNull(rarity, "rarity"));
    }

    /**
     * 체크인 한 번의 보상 줄(순수 함수). 지역 기본은 항상, 시·도 첫 발 도장과 선점 보너스는 해당할 때만.
     *
     * @param firstInProvince 받는 사람 기준으로 그 시·도에 처음 발을 들였는지
     * @param firstClaim      지도 안에서 그 지역을 처음 칠했는지(선점)
     */
    public List<RewardLine> checkIn(Rarity rarity, boolean firstInProvince, boolean firstClaim) {
        List<RewardLine> lines = new ArrayList<>(3);
        lines.add(new RewardLine(RewardSource.REGION_BASE, base(rarity)));
        if (firstInProvince) lines.add(new RewardLine(RewardSource.PROVINCE_FIRST, provinceFirstBonus));
        if (firstClaim) lines.add(new RewardLine(RewardSource.FIRST_CLAIM, claimBonus));
        return List.copyOf(lines);
    }

    /** 도감 세트 완성 보너스. */
    public RewardLine setComplete() {
        return new RewardLine(RewardSource.SET_COMPLETE, setCompleteBonus);
    }
}
