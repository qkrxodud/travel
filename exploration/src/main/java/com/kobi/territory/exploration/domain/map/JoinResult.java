package com.kobi.territory.exploration.domain.map;

import java.util.Optional;

/**
 * ExpeditionMap.join 의 결과.
 *
 * @param rejoined          탈퇴 유예 안에 돌아왔는지(방문 복구 대상)
 * @param expiredDeparture  유예가 끝났지만 아직 배치가 지우지 않은 예전 탈퇴 기록을 이번에 지웠으면 그 기록(숨긴 방문 삭제 대상), 없으면 null
 */
public record JoinResult(Member member, boolean rejoined, Departure expiredDeparture) {

    public Optional<Departure> purgedDeparture() {
        return Optional.ofNullable(expiredDeparture);
    }
}
