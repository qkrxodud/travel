package com.kobi.territory.wardrobe.api.query;

/**
 * 꾸미기 공개 Query(4단계). 공유(자랑 카드·공개 프로필)가 장면 요약을 그릴 때 쓴다 — 착용·점수·보유 수만, 즐겨찾기·획득 이력은 없다.
 */
public interface SceneQuery {

    /** 탐험가의 장면 요약. 장면·가방이 아직 없으면 빈 요약(점수 0). */
    SceneSummaryView summaryOf(String explorerId);
}
