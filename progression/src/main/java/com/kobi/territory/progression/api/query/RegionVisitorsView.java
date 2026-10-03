package com.kobi.territory.progression.api.query;

/** 지역 하나를 활성으로 가진 탐험가 수(공개 Query DTO, 5단계 region_stats). */
public record RegionVisitorsView(String regionCode, int visitorCount) {}
