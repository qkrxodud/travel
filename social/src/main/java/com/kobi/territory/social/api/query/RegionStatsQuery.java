package com.kobi.territory.social.api.query;

import java.util.List;

/**
 * 지역별 방문자 수 공개 Query(8단계) — 일 1회 집계(region_stats) 그대로. 카탈로그의 이번 주 미스터리 지역 고르기("덜 알려진 곳")가
 * 쓴다 — 카탈로그는 다른 컨텍스트를 참조하지 않으므로 조립 모듈(app-api)이 카탈로그의 RegionVisitorCounts 포트로 이어 준다.
 */
public interface RegionStatsQuery {

    /** 집계된 지역마다 방문자 수와 모집단(집계 전이면 빈 목록). */
    List<RegionVisitorsView> regionVisitors();

    record RegionVisitorsView(String regionCode, int visitorCount, int population) {}
}
