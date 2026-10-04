package com.kobi.territory.catalog.domain.reward;

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
 * 입력은 사실 값(희귀도·시·도 첫 방문·선점·이번 주 미스터리 지역)뿐이고 영토(Territory)를 모른다.
 * 8단계: 이번 주 미스터리 지역 보너스(mysteryBonus — 주마다 1회는 진행이 refId 로 지킨다)와 시·도 정복 보상(provinceConquestBonus).
 * 9단계: 계절 한정 테마 완성(seasonCompleteBonus)·재방문 도장(revisitStampBonus)·가고 싶은 곳 다녀옴(wishFulfilledBonus).
 */
public record RewardRules(Map<Rarity, Integer> xpByRarity, int provinceFirstBonus, int setCompleteBonus, int claimBonus,
                          int mysteryBonus, int provinceConquestBonus, int seasonCompleteBonus, int revisitStampBonus,
                          int wishFulfilledBonus) {
    public RewardRules {
        for (Rarity rarity : Rarity.values()) {
            if (xpByRarity == null || !xpByRarity.containsKey(rarity)) {
                throw new IllegalArgumentException("희귀도별 XP 누락: " + rarity);
            }
        }
        if (mysteryBonus < 0 || provinceConquestBonus < 0 || seasonCompleteBonus < 0 || revisitStampBonus < 0
            || wishFulfilledBonus < 0) {
            throw new IllegalArgumentException("보너스는 0 이상");
        }
        xpByRarity = Map.copyOf(new EnumMap<>(xpByRarity));
    }

    /** 계절 한정·재방문 도장·가고 싶은 곳 보상이 없던 규칙(8단계 값). */
    public RewardRules(Map<Rarity, Integer> xpByRarity, int provinceFirstBonus, int setCompleteBonus, int claimBonus,
                       int mysteryBonus, int provinceConquestBonus) {
        this(xpByRarity, provinceFirstBonus, setCompleteBonus, claimBonus, mysteryBonus, provinceConquestBonus, 0, 0, 0);
    }

    /** 미스터리·시·도 정복 보상이 없던 규칙(1~7단계 값). */
    public RewardRules(Map<Rarity, Integer> xpByRarity, int provinceFirstBonus, int setCompleteBonus, int claimBonus) {
        this(xpByRarity, provinceFirstBonus, setCompleteBonus, claimBonus, 0, 0);
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
        return checkIn(rarity, firstInProvince, firstClaim, false);
    }

    /**
     * 체크인 한 번의 보상 줄 + 이번 주 미스터리 지역이면 미스터리 보너스 줄(8단계). 주마다 한 번인지는 받는 쪽(진행)이 장부로 지킨다.
     *
     * @param mysteryOfWeek 체크인한 지역이 처리 시각이 속한 주의 미스터리 지역인지
     */
    public List<RewardLine> checkIn(Rarity rarity, boolean firstInProvince, boolean firstClaim, boolean mysteryOfWeek) {
        List<RewardLine> lines = new ArrayList<>(4);
        lines.add(new RewardLine(RewardSource.REGION_BASE, base(rarity)));
        if (firstInProvince) lines.add(new RewardLine(RewardSource.PROVINCE_FIRST, provinceFirstBonus));
        if (firstClaim) lines.add(new RewardLine(RewardSource.FIRST_CLAIM, claimBonus));
        if (mysteryOfWeek && mysteryBonus > 0) lines.add(new RewardLine(RewardSource.MYSTERY_BONUS, mysteryBonus));
        return List.copyOf(lines);
    }

    /** 도감 세트 완성 보너스. */
    public RewardLine setComplete() {
        return new RewardLine(RewardSource.SET_COMPLETE, setCompleteBonus);
    }

    /** 이번 주 미스터리 지역 보너스. */
    public RewardLine mystery() {
        return new RewardLine(RewardSource.MYSTERY_BONUS, mysteryBonus);
    }

    /** 시·도 정복(탐험가 단위로 한 시·도의 현행 지역을 모두 칠함) 보상. */
    public RewardLine provinceConquest() {
        return new RewardLine(RewardSource.PROVINCE_CONQUEST, provinceConquestBonus);
    }

    /** 계절 한정 테마 회차 완성 보상(9단계). */
    public RewardLine seasonComplete() {
        return new RewardLine(RewardSource.SEASON_COMPLETE, seasonCompleteBonus);
    }

    /** 재방문 도장 보상(9단계). */
    public RewardLine revisitStamp() {
        return new RewardLine(RewardSource.REVISIT_STAMP, revisitStampBonus);
    }

    /** 가고 싶은 곳을 다녀옴 보상(9단계). */
    public RewardLine wishFulfilled() {
        return new RewardLine(RewardSource.WISH_FULFILLED, wishFulfilledBonus);
    }
}
