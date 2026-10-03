package com.kobi.territory.sharing.application;

/**
 * 친구 관계 포트(5단계): 두 탐험가가 서로 팔로우한 친구(맞팔로우)인지. 공개 범위 FRIENDS 판정에 쓴다. 친구 관계의 주인은 소셜
 * 컨텍스트인데, 소셜도 공유의 공개 범위를 물어야 해서(피드·비교) 둘이 서로를 직접 참조하면 순환이 된다 — 그래서 공유는 포트만 두고
 * 조립 모듈(app-api)이 소셜의 공개 Query(FriendshipQuery)로 구현한다(4단계 ProfileJoinGate 방식).
 */
public interface FriendDirectory {

    boolean mutualFriends(String explorerId, String otherExplorerId);
}
