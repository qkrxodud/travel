package com.kobi.territory.progression.infra.entity;

/** streak_freeze 집계 결과 행(탐험가 → 가진 보호권 수 = 장부 합계) — Spring Data 인터페이스 프로젝션(12단계). */
public interface FreezeHeldRow {

    String getExplorerId();

    long getHeld();
}
