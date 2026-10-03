package com.kobi.territory.catalog.domain.reward;

/**
 * 보상 XP 출처. 체크인 보상(지역 기본·시·도 첫 발·선점·이번 주 미스터리)과 2차 보상(세트 완성·퀘스트·시·도 정복·연속 탐험 마일스톤).
 * 8단계: MYSTERY_BONUS·PROVINCE_CONQUEST·STREAK_MILESTONE 추가(끝에 — 하위 호환).
 */
public enum RewardSource {
    REGION_BASE, PROVINCE_FIRST, FIRST_CLAIM, SET_COMPLETE, QUEST, MYSTERY_BONUS, PROVINCE_CONQUEST, STREAK_MILESTONE
}
