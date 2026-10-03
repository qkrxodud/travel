package com.kobi.territory.social.domain.friendship;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.domain.SocialError;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 일급 컬렉션: 한 탐험가가 팔로우하는 관계들(나가는 쪽). 팔로우·언팔로우 커맨드의 판단(로그인 필요·중복 불가·없는 관계 해제 불가)을 한다 —
 * 같은 쌍이 동시에 들어오는 경합은 저장소 PK 가 막는다.
 */
public final class Followings {

    private final ExplorerId follower;
    private final boolean accountLinked;
    private final Map<ExplorerId, Friendship> byFollowee;

    private Followings(ExplorerId follower, boolean accountLinked, Collection<Friendship> friendships) {
        this.follower = Objects.requireNonNull(follower, "follower");
        this.accountLinked = accountLinked;
        this.byFollowee = new LinkedHashMap<>();
        friendships.forEach(friendship -> {
            if (!friendship.from(follower)) throw new IllegalArgumentException("다른 탐험가의 팔로우: " + friendship);
            byFollowee.put(friendship.followee(), friendship);
        });
    }

    /** @param accountLinked follower 가 계정(구글 로그인) 연결된 탐험가인지 — 팔로우는 로그인한 사람만 */
    public static Followings of(ExplorerId follower, boolean accountLinked, Collection<Friendship> friendships) {
        return new Followings(follower, accountLinked, friendships);
    }

    /**
     * followee 팔로우(커맨드 follow). 로그인하지 않았으면 LOGIN_REQUIRED, 자기 자신이면 CANNOT_FOLLOW_SELF.
     * 대상이 숨은 프로필(revealable=false — 프로필이 나에게 보이지 않고 나를 팔로우하지도 않음)이면 존재를 알리지 않는다: 새 관계는 기록하되
     * 결과는 revealed=false(호출자가 없는 handle 과 같은 404 로 응답), 이미 팔로우 중이어도 409 대신 같은 결과. 보이는 대상의 중복은
     * ALREADY_FOLLOWING(QA P3-3 — 팔로우 경로의 404 일관성, 숨은 대상과 맞팔하는 길은 남긴다).
     *
     * @param revealable 대상 프로필이 나에게 보이거나 대상이 이미 나를 팔로우하는지
     */
    public FollowResult follow(ExplorerId followee, boolean revealable, Instant at) {
        requireAccount();
        if (byFollowee.containsKey(followee)) {
            if (revealable) throw SocialError.ALREADY_FOLLOWING.exception();
            return new FollowResult(Optional.empty(), false);
        }
        Friendship started = Friendship.start(follower, followee, at);
        byFollowee.put(followee, started);
        return new FollowResult(Optional.of(started), revealable);
    }

    /**
     * 언팔로우(커맨드 unfollow) — 멱등(QA P3-3): 팔로우하고 있지 않아도 오류 없이 빈 값(없는 handle·숨은 대상·관계 없음이 같은 응답).
     * @return 끝난 관계(삭제 대상)
     */
    public Optional<Friendship> unfollow(ExplorerId followee) {
        return Optional.ofNullable(byFollowee.remove(followee));
    }

    /**
     * 로그인(계정 연결) 확인 — 호출자는 handle 을 찾기 <b>전에</b> 부른다(QA r2 P2-A: 익명이 있는 handle 은 401, 없는 handle 은 404 로
     * 존재를 가르지 않게). 로그인하지 않았으면 LOGIN_REQUIRED.
     */
    public void requireAccount() {
        if (!accountLinked) throw SocialError.LOGIN_REQUIRED.exception();
    }

    public boolean contains(ExplorerId followee) {
        return byFollowee.containsKey(followee);
    }

    public Set<ExplorerId> followeeIds() {
        return Set.copyOf(byFollowee.keySet());
    }
}
