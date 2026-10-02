package com.kobi.territory.exploration.api;

import java.util.List;

/**
 * 탐험 컨텍스트 공개 Query. 공유(공개 프로필·카드) 등 하류가 영토 요약을 읽을 때 쓴다.
 * 방문 메모·사진은 노출하지 않는다(프라이버시 — 색칠과 집계만).
 */
public interface TerritoryQuery {

    /** 지도에 칠해진 지역 코드(중복 제거, KR-xxxxx). */
    List<String> claimedRegionCodes(String mapId);

    /** 탐험가의 개인 지도 id. */
    String personalMapId(String explorerId);
}
