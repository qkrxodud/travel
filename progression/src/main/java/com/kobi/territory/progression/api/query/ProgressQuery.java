package com.kobi.territory.progression.api.query;

/** 진행 공개 Query(4단계). 공유(자랑 카드·공개 프로필)가 레벨·칭호·스트릭을 그릴 때 쓴다. */
public interface ProgressQuery {

    /** 탐험가의 진행 요약. 아직 이벤트가 없으면 시작 상태(Lv.1). 탐험가가 없으면 404 EXPLORER_NOT_FOUND. */
    ProgressSummaryView summaryOf(String explorerId);
}
