package com.kobi.territory.exploration.domain.map;

import java.util.Optional;

/**
 * ExpeditionMap.handOver(병합 시 자리 넘기기) 의 결과. 병합되는 탐험가의 방문·선점은 탈퇴가 아니라 재귀속(MemberReassigned)으로 간다.
 *
 * @param joinedMember     자리를 이어받아 합류한 계정 탐험가(이미 멤버였으면 null)
 * @param rejoined         계정 탐험가가 유예 안에 돌아온 재가입인지(숨긴 방문 복구 대상)
 * @param expiredDeparture 계정 탐험가의 유예 끝난 예전 탈퇴 기록을 이번에 지웠으면 그 기록(숨긴 방문 삭제 대상), 없으면 null
 */
public record Handover(Member joinedMember, boolean rejoined, Departure expiredDeparture) {

    public Optional<Member> joined() {
        return Optional.ofNullable(joinedMember);
    }

    public Optional<Departure> purgedDeparture() {
        return Optional.ofNullable(expiredDeparture);
    }
}
