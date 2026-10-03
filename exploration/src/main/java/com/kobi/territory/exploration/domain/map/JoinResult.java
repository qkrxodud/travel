package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Optional;

/**
 * ExpeditionMap.join 의 결과.
 *
 * @param rejoined          탈퇴 유예 안에 돌아왔는지(방문 복구 대상)
 * @param expiredDeparture  유예가 끝났지만 아직 배치가 지우지 않은 예전 탈퇴 기록을 이번에 지웠으면 그 기록(숨긴 방문 삭제 대상), 없으면 null
 * @param invitedBy         초대한 탐험가(4단계 초대 보상) — 초대코드 합류는 지도장, 프로필 링크 합류는 프로필 주인
 */
public record JoinResult(Member member, boolean rejoined, Departure expiredDeparture, ExplorerId invitedBy) {

    public Optional<Departure> purgedDeparture() {
        return Optional.ofNullable(expiredDeparture);
    }
}
