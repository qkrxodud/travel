package com.kobi.territory.exploration.domain.revisit;

/**
 * 재방문 도장을 받은 결과 → application 이 공개 이벤트 RevisitStamped 로 적재한다.
 *
 * @param firstYear  그 지역을 처음 칠한 해
 * @param stampCount 이 도장을 포함한 도장 수
 */
public record StampResult(RevisitStamp stamp, int firstYear, int stampCount) {}
