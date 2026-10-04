package com.kobi.territory.exploration.api.query;

import java.time.Instant;
import java.util.List;

/**
 * 재방문 도장 공개 Query(9단계). 진행·꾸미기 재계산이 도장으로 정해지는 보상(XP·뱃지, 지역 특산물의 2회차 색 변형)을 다시 맞출 때 쓴다.
 */
public interface RevisitQuery {

    /** 이 탐험가의 도장 전부(받은 순). 없으면 빈 목록. */
    List<RevisitStampView> stampsOf(String explorerId);

    /** @param year 도장 연도 */
    record RevisitStampView(String regionCode, int year, Instant stampedAt) {}
}
