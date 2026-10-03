package com.kobi.territory.exploration.api.query;

/**
 * 공개 프로필에 보이는 합류 가능 공유 지도(공개 Query DTO, 4단계). 초대코드·멤버 id 는 싣지 않는다.
 *
 * @param memberCount 지금 멤버 수(탈퇴 유예 중 제외)
 * @param maxMembers  멤버 상한 — memberCount 가 같으면 가득 참(합류 시 MAP_FULL)
 */
public record ProfileMapView(String mapId, String name, int memberCount, int maxMembers) {
    public boolean full() {
        return memberCount >= maxMembers;
    }
}
