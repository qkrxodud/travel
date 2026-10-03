package com.kobi.territory.common.event;

import java.time.Instant;

/**
 * 재계산 예약 포트(구현은 app-api). 병합(claimExplorer)처럼 이벤트(RegionVisited 등) 없이 영토가 바뀐 탐험가를 적어 두면, 조립 모듈의
 * 배치가 재계산 보류 규칙(그 탐험가·지도의 미전달 이벤트 없음)을 만족할 때 진행·인벤토리를 Territory 로부터 다시 만든다
 * (일관성 원칙 3 — 재계산 가능성). 구독자 안에서 바로 재계산하면 지금 처리 중인 이벤트 자체가 "미전달"이라 늘 보류되므로 예약으로 뗀다.
 */
public interface RecalculationRequests {

    /** 호출자 트랜잭션 안에서 예약한다. 이미 예약돼 있으면 그대로 둔다(멱등). 시각은 호출자가 정한다. */
    void request(String explorerId, String reason, Instant requestedAt);
}
