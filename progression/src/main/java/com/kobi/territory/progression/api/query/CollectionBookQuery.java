package com.kobi.territory.progression.api.query;

import java.util.List;

/** 도감(지도 단위) 공개 Query. 꾸미기가 새 멤버에게 이미 완성된 테마 보상(세트 배경)을 줄 때 쓴다. */
public interface CollectionBookQuery {

    /** 지도에서 완성 기록이 있는 테마(세트) id. 도감 행이 없으면 빈 목록. */
    List<String> completedSetIds(String mapId);

    /** 지도에서 완성 기록이 있는 테마(세트)와 완성 시각(3단계 파트 B — 세트 보상 기간 판정은 완성 시각, Q-R2-1). */
    List<CompletedSetView> completedSets(String mapId);

    /** 지도에서 완성 기록이 있는 계절 회차와 완성 시각·수령자(9단계). 도감 행이 없으면 빈 목록. */
    List<CompletedSeasonView> completedSeasons(String mapId);
}
