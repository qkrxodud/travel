package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.wardrobe.domain.item.GrantKind;

/** 보유 출처. REGION 만 회수 대상(체크인 취소, 탐험가 단위)이고 SET_REWARD·EVENT 는 회수 없음(취소 비대칭). */
public enum ItemSource {
    REGION, SET_REWARD, EVENT;

    static ItemSource of(GrantKind grantKind) {
        return switch (grantKind) {
            case REGION_VISIT -> REGION;
            case THEME_COMPLETE -> SET_REWARD;
            case PERIOD_CHECK_IN, PROVINCE_CHECK_IN, MANUAL -> EVENT;
        };
    }
}
