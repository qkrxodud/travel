package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.common.model.Rarity;
import java.util.List;

/**
 * 체크인 보상 계산 포트(리더 결정 D1). 구현은 application 계층이 카탈로그의 공개 보상 함수(RewardCalculator)에 위임한다 —
 * 진행 컨텍스트가 실제 지급에 쓰는 것과 같은 함수다. 입력은 사실 값뿐(영토를 넘기지 않는다).
 */
@FunctionalInterface
public interface CheckInRewards {
    /** @param mysteryOfWeek 이번 주 미스터리 지역인지(8단계 — 주마다 한 번인지는 진행이 지킨다, 미리보기는 최대 보상) */
    List<CheckInPreview.XpLine> award(Rarity rarity, boolean firstInProvince, boolean firstClaim, boolean mysteryOfWeek);
}
