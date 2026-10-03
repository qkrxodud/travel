package com.kobi.territory.catalog.api.query;

import com.kobi.territory.common.model.Rarity;
import java.time.LocalDate;

/**
 * 아이템 정의 공개 표현. slot: HAND|BADGE|HAT|BAG|PET|BG|PROP.
 * grantRule: REGION_VISIT(grantRef = 지역 코드) | PERIOD_CHECK_IN | PROVINCE_CHECK_IN(grantRef = 시·도 코드) |
 * THEME_COMPLETE(grantRef = 세트 id) | MANUAL | INVITATION(grantRef = HOST·GUEST, 4단계 초대 보상) |
 * PROVINCE_COMPLETE(grantRef = 시·도 코드, 8단계 시·도 정복) | STREAK_MILESTONE(grantRef = 개월 수, 8단계 연속 탐험 마일스톤). validFrom·validTo 는 지급 유효 기간(양 끝 포함, null 이면 열림).
 * (3단계: grantRule 이하 4개 필드 추가 — 하위 호환)
 */
public record ItemView(String itemId, String regionCode, String name, String emoji, String slot, Rarity tier, String theme,
                       Look look, String grantRule, String grantRef, LocalDate validFrom, LocalDate validTo) {
    public record Look(String type, String primary, String secondary) {}
}
