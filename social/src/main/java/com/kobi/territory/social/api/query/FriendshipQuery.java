package com.kobi.territory.social.api.query;

/**
 * 소셜 공개 Query(5단계): 두 탐험가가 서로 팔로우한 친구(맞팔로우)인지. 공유의 FRIENDS 공개 범위(프로필·카드·VS)가 쓴다 — 공유는
 * 소셜을 직접 참조하지 않고 조립 모듈(app-api)이 공유의 FriendDirectory 포트로 이어 준다(순환 방지).
 */
public interface FriendshipQuery {

    boolean mutualFriends(String explorerId, String otherExplorerId);
}
