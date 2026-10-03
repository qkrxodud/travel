package com.kobi.territory.progression.domain.policy;

/** XP 장부 항목의 출처. 8단계 3종(미스터리·시·도 정복·연속 탐험 마일스톤)은 끝에 — 저장 값(xp_ledger.source)이라 이름을 바꾸지 않는다. */
public enum XpSource {
    /** 지역 기본 XP(탐험가당 지역당 활성 1개, 취소 시 음수 항목으로 회수). */
    REGION_BASE,
    /** 시·도 첫 발 도장(탐험가당 시·도당 1회, 회수 없음). */
    PROVINCE_FIRST,
    /** 선점 보너스(지도마다·수령자마다 1회). */
    FIRST_CLAIM,
    /** 도감 세트 완성(탐험가당 세트당 1회). */
    SET_COMPLETE,
    /** 퀘스트 보상(보드·퀘스트당 1회). */
    QUEST,
    /** 이번 주 미스터리 지역 보너스(탐험가당 주당 1회, 회수 없음 — 8단계). */
    MYSTERY_BONUS,
    /** 시·도 정복(탐험가당 시·도당 1회, 회수 없음 — 8단계). */
    PROVINCE_CONQUEST,
    /** 연속 탐험 마일스톤(탐험가당 마일스톤당 1회, 끊겼다 다시 쌓아도 다시 없음 — 8단계). */
    STREAK_MILESTONE
}
