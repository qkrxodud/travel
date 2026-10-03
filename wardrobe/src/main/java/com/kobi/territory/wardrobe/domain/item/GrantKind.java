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
    MANUAL,
    /** 초대로 공유 지도에 처음 합류(초대자·피초대자, 4단계) — 회수 없음 */
    INVITATION,
    /** 시·도 정복(탐험가 단위로 한 시·도의 현행 지역을 모두 칠함, 8단계) — 회수 없음 */
    PROVINCE_COMPLETE,
    /** 연속 탐험 마일스톤(8단계) — 회수 없음 */
    STREAK_MILESTONE;

    /** 방문으로 받는 종류(지역·기간·시·도) — 근거 방문이 없으면 가질 수 없다(회수·재계산 정리 대상). */
    public boolean byVisit() {
        return this == REGION_VISIT || this == PERIOD_CHECK_IN || this == PROVINCE_CHECK_IN;
    }

    /**
     * 재계산(영토·도감 재생)으로 다시 만들 수 있는 종류인지 — 방문형과 테마 완성. 수동·초대 보상은 재생할 근거가 없어, 계정 병합 때
     * 익명 탐험가의 것을 계정 탐험가로 옮긴다(4단계 QA P3-6). 시·도 정복·마일스톤(8단계)도 그 탐험가의 진행 기록에 묶인 보상이라
     * 계정 탐험가의 영토로 다시 만들어지지 않는다 — 병합 때 옮긴다.
     */
    public boolean replayable() {
        return byVisit() || this == THEME_COMPLETE;
    }
}
