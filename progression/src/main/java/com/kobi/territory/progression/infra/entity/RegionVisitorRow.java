package com.kobi.territory.progression.infra.entity;

/**
 * explorer_region 집계 결과 행(지역 → 활성 탐험가 수) — Spring Data 인터페이스 프로젝션. 탐험가 목록을 나눠 물은 부분 합이라 도메인 값
 * (RegionVisitorTally)으로는 어댑터가 지역별로 더한 뒤에 만든다.
 */
public interface RegionVisitorRow {

    String getRegionCode();

    long getVisitorCount();
}
