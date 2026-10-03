package com.kobi.territory.social.domain.friendship;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Objects;

/**
 * 나와 다른 탐험가 한 명의 관계(GET /friends 한 줄).
 *
 * @param following 내가 팔로우함
 * @param follower  그가 나를 팔로우함
 */
public record FriendRelation(ExplorerId explorerId, boolean following, boolean follower) {
    public FriendRelation {
        Objects.requireNonNull(explorerId, "explorerId");
    }

    /** 서로 팔로우 = 친구. */
    public boolean mutual() {
        return following && follower;
    }
}
