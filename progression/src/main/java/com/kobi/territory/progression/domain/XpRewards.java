package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.Rarity;
import java.util.List;

/**
 * 보상 계산 포트(D1). 구현은 application 이 카탈로그 공개 함수(RewardCalculator)에 위임한다 — 체크인 미리보기와 같은 함수.
 */
public interface XpRewards {

    List<XpAward> checkIn(Rarity rarity, boolean firstInProvince, boolean firstClaim);

    int setComplete();
}
