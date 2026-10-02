package com.kobi.territory.progression.domain;

/** XP 장부 항목의 출처. */
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
    QUEST
}
