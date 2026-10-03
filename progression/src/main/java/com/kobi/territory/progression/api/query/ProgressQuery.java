package com.kobi.territory.progression.api.query;

/** 진행 공개 Query(4단계). 공유(자랑 카드·공개 프로필)가 레벨·칭호·스트릭을 그릴 때 쓴다. */
public interface ProgressQuery {

    /** 탐험가의 진행 요약. 아직 이벤트가 없으면 시작 상태(Lv.1). 탐험가가 없으면 404 EXPLORER_NOT_FOUND. */
    ProgressSummaryView summaryOf(String explorerId);

    /** 받은 연속 탐험 마일스톤·정복한 시·도(8단계). 진행 기록이 없으면 빈 목록(탐험가 확인은 하지 않는다 — 재계산 경로). */
    AchievementsView achievementsOf(String explorerId);
}
