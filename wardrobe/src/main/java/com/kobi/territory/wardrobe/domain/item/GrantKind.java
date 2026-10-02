package com.kobi.territory.wardrobe.domain.item;

/** 아이템이 어떤 규칙으로 지급되는지(카탈로그 grantRule 의 꾸미기 쪽 표현). 보유 출처(ItemSource)와 회수 여부를 정한다. */
public enum GrantKind {
    /** 지역 방문(지역 특산물) — 취소 시 회수 */
    REGION_VISIT,
    /** 기간 내 체크인(이슈) — 회수 없음 */
    PERIOD_CHECK_IN,
    /** 특정 시·도 체크인(이슈) — 회수 없음 */
    PROVINCE_CHECK_IN,
    /** 테마(세트) 완성 — 회수 없음 */
    THEME_COMPLETE,
    /** 수동 지급(자동 지급 없음) */
    MANUAL;

    /** 방문으로 받는 종류(지역·기간·시·도) — 근거 방문이 없으면 가질 수 없다(회수·재계산 정리 대상). */
    public boolean byVisit() {
        return this == REGION_VISIT || this == PERIOD_CHECK_IN || this == PROVINCE_CHECK_IN;
    }
}
