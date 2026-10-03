package com.kobi.territory.social.domain.friendship;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.domain.SocialError;
import java.time.Instant;
import java.util.Objects;

/**
 * 팔로우 관계 한 건 = 애그리거트 하나(§2-7, friendship PK(from_id, to_id)). follower 가 followee 를 팔로우한다(한쪽 방향).
 * "친구"는 서로 팔로우한 사이(맞팔로우) — {@link SocialCircle}. 불변식: 자기 팔로우 불가(여기), 중복 불가({@link Followings} + PK).
 */
public final class Friendship {

    private final ExplorerId follower;
    private final ExplorerId followee;
    private final Instant since;

    private Friendship(ExplorerId follower, ExplorerId followee, Instant since) {
        this.follower = Objects.requireNonNull(follower, "follower");
        this.followee = Objects.requireNonNull(followee, "followee");
        this.since = Objects.requireNonNull(since, "since");
        if (follower.equals(followee)) throw SocialError.CANNOT_FOLLOW_SELF.exception();
    }

    /** 팔로우 시작(커맨드 follow). 자기 자신이면 CANNOT_FOLLOW_SELF. */
    static Friendship start(ExplorerId follower, ExplorerId followee, Instant at) {
        return new Friendship(follower, followee, at);
    }

    public static Friendship restore(ExplorerId follower, ExplorerId followee, Instant since) {
        return new Friendship(follower, followee, since);
    }

    public boolean from(ExplorerId explorerId) {
        return follower.equals(explorerId);
    }

    public boolean to(ExplorerId explorerId) {
        return followee.equals(explorerId);
    }

    public ExplorerId follower() { return follower; }
    public ExplorerId followee() { return followee; }
    public Instant since() { return since; }

    @Override
    public boolean equals(Object other) {
        return other instanceof Friendship friendship && follower.equals(friendship.follower) && followee.equals(friendship.followee);
    }

    @Override
    public int hashCode() {
        return Objects.hash(follower, followee);
    }

    @Override
    public String toString() {
        return "Friendship[" + follower + " → " + followee + "]";
    }
}
