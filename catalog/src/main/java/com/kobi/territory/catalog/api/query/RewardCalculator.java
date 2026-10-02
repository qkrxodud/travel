package com.kobi.territory.catalog.api.query;

import com.kobi.territory.common.model.Rarity;
import java.util.List;

/**
 * 보상 계산 공개 Query(리더 결정 D1). 본체는 catalog.domain.RewardRules 의 순수 함수이고, 탐험(체크인 미리보기)과
 * 진행(실제 지급)이 이 인터페이스로 같은 함수를 호출한다. 입력은 사실 값뿐이며 영토를 모른다.
 */
public interface RewardCalculator {

    /** 체크인 한 번의 보상 줄: 지역 기본 + (시·도 첫 발) + (선점). */
    List<RewardLineView> checkIn(Rarity rarity, boolean firstInProvince, boolean firstClaim);

    /** 도감 세트 완성 보너스. */
    RewardLineView setComplete();
}
