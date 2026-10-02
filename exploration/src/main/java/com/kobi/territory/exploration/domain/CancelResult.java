package com.kobi.territory.exploration.domain;

/**
 * Territory.cancelVisit 의 결과.
 *
 * @param wasClaim 취소된 방문이 그 지역의 선점(지도 내 최초 체크인)이었는지
 * @param remaining 취소 후 이 멤버의 영토 수(이 지도 기준)
 */
public record CancelResult(MapId mapId, Visit visit, boolean wasClaim, int remaining) {}
