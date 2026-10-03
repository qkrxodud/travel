package com.kobi.territory.social.domain.stats;

/**
 * 집계 이상치: 지역 방문자 수가 모집단보다 많았다(원천 집계가 어긋남 — 정상 데이터에선 생기지 않는다). 배치는 멈추지 않고 그 지역만
 * 모집단으로 맞추며, 호출자가 ERROR 로그·카운터로 남긴다(QA r2 P3-B).
 */
public record RegionOverflow(String regionCode, int counted, int population) {}
