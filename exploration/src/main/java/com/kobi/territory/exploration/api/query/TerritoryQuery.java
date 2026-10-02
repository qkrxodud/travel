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

    /** 탐험가가 지금 멤버인 지도 id 전부(탈퇴 유예 중인 지도 제외). */
    List<String> mapIdsOf(String explorerId);

    /** 모든 탐험가 id(재계산 배치 전체 실행용). */
    List<String> explorerIds();

    /**
     * 지도의 현재 방문을 처리 시각 순으로 재생한 RegionVisited 목록(사실 값 nth·isFirstInProvince·isFirstClaim 재계산).
     * 진행·도감·퀘스트 재계산 배치(RecalculateService) 전용. 취소된 방문·탈퇴로 숨긴 방문은 포함하지 않는다.
     * memberIds 는 지금 멤버(재생은 과거 시점 멤버를 모른다 — 재계산에서 새로 생기는 완성에만 쓰인다).
     */
    List<RegionVisited> visitHistory(String mapId);

    /** 지도의 현재 멤버 id(탈퇴 유예 중 제외). 지도가 없으면 빈 목록. */
    List<String> memberIdsOf(String mapId);

    /** 이 탐험가가 어느 지도에든 이 지역 방문(숨기지 않은 것)을 갖고 있는지 — 탐험가 단위 회수 판단 보조. */
    boolean visitsRegionAnywhere(String explorerId, String regionCode);
}
