package com.kobi.territory.exploration.domain.territory;

/**
 * 어떤 멤버가 어떤 지역을 지금 체크인한다면 성립하는 사실들. Territory가 자기 상태로 계산한다.
 * 체크인 결과·미리보기·RegionVisited 이벤트가 모두 이 값을 쓰므로 예상과 실제가 어긋나지 않는다.
 *
 * @param alreadyVisited  이 멤버가 이미 방문 상태인지
 * @param nth             체크인하면 이 멤버의 몇 번째 영토인지(이 지도 기준)
 * @param firstInProvince 이 멤버가 해당 시·도에서 처음인지
 * @param firstClaim      지도에서 아무도 이 지역을 체크인하지 않았는지(선점)
 */
public record VisitFacts(boolean alreadyVisited, int nth, boolean firstInProvince, boolean firstClaim) {}
