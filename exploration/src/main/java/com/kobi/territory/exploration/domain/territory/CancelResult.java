package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.exploration.domain.map.MapId;

/**
 * Territory.cancelVisit 의 결과.
 *
 * @param wasClaim 취소된 방문이 그 지역의 선점(지도 내 최초 체크인)이었는지
 * @param remaining 취소 후 이 멤버의 영토 수(이 지도 기준)
 * @param regionStillOnMap 취소 후에도 그 지역이 지도에 (다른 멤버의 방문으로) 칠해져 있는지 — 도감(지도 단위) 회수 판단용(D2)
 */
public record CancelResult(MapId mapId, Visit visit, boolean wasClaim, int remaining, boolean regionStillOnMap) {}
