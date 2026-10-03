package com.kobi.territory.social.domain.friendship;

/**
 * 같은 팔로우 관계가 이미 저장돼 있다(동시 팔로우 경합의 PK 위반 — 저장소 어댑터가 번역). 어떤 응답으로 바꿀지는 호출자가
 * {@link FollowResult#duplicateError()} 로 정한다(숨은 대상이면 404).
 */
public class FriendshipAlreadyExists extends RuntimeException {

    public FriendshipAlreadyExists(Friendship friendship, Throwable cause) {
        super("이미 있는 팔로우: " + friendship, cause);
    }
}
