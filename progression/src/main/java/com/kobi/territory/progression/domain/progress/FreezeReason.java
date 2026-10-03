package com.kobi.territory.progression.domain.progress;

/** 보호권 장부 한 줄의 사유(8단계). 저장 값(streak_freeze.reason)이라 이름을 바꾸지 않는다. */
public enum FreezeReason {
    /** 한 달의 월간 퀘스트를 모두 보상 받음(그 달 1회). */
    MONTHLY_QUESTS,
    /** 연속 탐험 마일스톤 도달(마일스톤당 1회). */
    MILESTONE,
    /** 빈 달을 메워 스트릭을 지킴(그 달 1회, 음수). */
    USED
}
