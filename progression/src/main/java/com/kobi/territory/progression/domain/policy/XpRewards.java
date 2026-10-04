package com.kobi.territory.progression.domain.policy;

import com.kobi.territory.common.model.Rarity;
import java.util.List;

/**
 * 보상 계산 포트(D1). 구현은 application 이 카탈로그 공개 함수(RewardCalculator)에 위임한다 — 체크인 미리보기와 같은 함수.
 */
public interface XpRewards {

    List<XpAward> checkIn(Rarity rarity, boolean firstInProvince, boolean firstClaim);

    /** 체크인 보상 + 이번 주 미스터리 지역이면 미스터리 보너스 줄(8단계). */
    List<XpAward> checkIn(Rarity rarity, boolean firstInProvince, boolean firstClaim, boolean mysteryOfWeek);

    /** 도감 테마(세트) 완성 보너스. */
    int themeComplete();

    /** 시·도 정복 보상(8단계). */
    int provinceConquest();

    /** 계절 한정 테마 회차 완성 보상(9단계). */
    int seasonComplete();

    /** 재방문 도장 보상(9단계). */
    int revisitStamp();

    /** 가고 싶은 곳을 다녀옴 보상(9단계). */
    int wishFulfilled();
}
