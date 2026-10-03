package com.kobi.territory.catalog.application;

import java.util.List;

/**
 * 지역별 방문자 수 포트(8단계 — 이번 주 미스터리 지역의 "덜 알려진 곳" 판단). 카탈로그는 다른 컨텍스트를 참조하지 않으므로
 * app-api 가 소셜의 일 1회 집계(region_stats)를 이어 준다. 빈이 없으면(카탈로그만 띄운 경우) 모두 0으로 본다.
 */
public interface RegionVisitorCounts {

    /** 지역 코드 → 그 지역을 활성으로 가진 탐험가 수와 모집단(집계가 아직 없으면 빈 목록). */
    List<Count> counts();

    record Count(String regionCode, int visitors, int population) {}
}
