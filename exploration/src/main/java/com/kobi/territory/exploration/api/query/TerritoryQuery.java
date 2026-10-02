package com.kobi.territory.exploration.api.query;

import com.kobi.territory.exploration.api.event.RegionVisited;
import java.util.List;

/**
 * 탐험 컨텍스트 공개 Query. 하류(진행·공유 등)가 영토 요약·지도 접근을 확인할 때 쓴다.
 * 방문 메모·사진은 노출하지 않는다(프라이버시 — 색칠과 집계만).
 */
public interface TerritoryQuery {

    /** 지도에 칠해진 지역 코드(중복 제거, KR-xxxxx). */
    List<String> claimedRegionCodes(String mapId);

    /** 탐험가의 개인 지도 id. 탐험가가 없으면 404 EXPLORER_NOT_FOUND. */
    String personalMapId(String explorerId);

    /**
     * 요청한 탐험가가 볼 수 있는 지도 id(mapIdOrNull 이 null·공백이면 개인 지도).
     * 탐험가 없음 404 EXPLORER_NOT_FOUND, 지도 없음 404 MAP_NOT_FOUND, 멤버 아님 403 NOT_A_MEMBER.
     */
    String resolveMapId(String explorerId, String mapIdOrNull);

    /** 탐험가가 멤버인 지도 id 전부(2단계는 개인 지도 1개). */
    List<String> mapIdsOf(String explorerId);

    /** 모든 탐험가 id(재계산 배치 전체 실행용). */
    List<String> explorerIds();

    /**
     * 지도의 현재 방문을 처리 시각 순으로 재생한 RegionVisited 목록(사실 값 nth·isFirstInProvince·isFirstClaim 재계산).
     * 진행·도감·퀘스트 재계산 배치(RecalculateService) 전용. 취소된 방문은 포함하지 않는다.
     */
    List<RegionVisited> visitHistory(String mapId);
}
