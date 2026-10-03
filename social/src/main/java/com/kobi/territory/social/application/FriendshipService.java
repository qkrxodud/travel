package com.kobi.territory.social.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.social.domain.SocialError;
import com.kobi.territory.social.domain.friendship.FollowResult;
import com.kobi.territory.social.domain.friendship.Followings;
import com.kobi.territory.social.domain.friendship.FriendRelation;
import com.kobi.territory.social.domain.friendship.FriendshipAlreadyExists;
import com.kobi.territory.social.domain.friendship.FriendshipRepository;
import com.kobi.territory.social.domain.friendship.SocialCircle;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 친구(팔로우) 유스케이스. 팔로우 대상은 handle 로 찾는다(계정 연결된 활성 탐험가만 handle 이 있다). 판단(로그인 필요·자기 팔로우·중복·
 * 숨은 대상에게 존재를 알리지 않음)은 Followings, 맞팔로우·관계 목록은 SocialCircle 이 한다. 공개 범위는 ProfileAudience 포트로 묻는다.
 */
@Service
public class FriendshipService {

    private final FriendshipRepository friendships;
    private final ExplorerProfileQuery profiles;
    private final TerritoryQuery territories;
    private final ProfileAudience audience;
    private final Clock clock;

    public FriendshipService(FriendshipRepository friendships, ExplorerProfileQuery profiles, TerritoryQuery territories,
                             ProfileAudience audience, Clock clock) {
        this.friendships = friendships;
        this.profiles = profiles;
        this.territories = territories;
        this.audience = audience;
        this.clock = clock;
    }

    /**
     * POST /friends/{handle}. 없는 handle 이면 PROFILE_NOT_FOUND. 숨은 대상(PRIVATE·친구 아닌 FRIENDS, 나를 팔로우하지 않음)이면 팔로우는
     * 기록하고 false — 호출자가 없는 handle 과 같은 404 로 응답한다(QA P3-3).
     *
     * @return 대상의 존재를 응답으로 알려도 되는지
     */
    @Transactional
    public boolean follow(ExplorerId me, String handle) {
        territories.personalMapId(me.value());
        Followings followings = Followings.of(me, profiles.accountLinked(me.value()), friendships.outgoing(me));
        followings.requireAccount();               // handle 을 찾기 전에 — 익명은 있는 handle·없는 handle 모두 401(QA r2 P2-A)
        SocialCircle circle = circleOf(me);        // 없는 handle 경로도 같은 조회를 한다(응답 시간 차이를 줄인다)
        ExplorerId target = byHandle(handle);
        boolean revealable = circle.followedBy(target) || audience.visibleTo(target.value(), me.value());
        FollowResult result = followings.follow(target, revealable, clock.instant());
        try {
            result.started().ifPresent(friendships::add);
        } catch (FriendshipAlreadyExists concurrent) {
            throw result.duplicateError();         // 동시 경합: 숨은 대상이면 409 대신 같은 404
        }
        return result.revealed();
    }

    /**
     * 팔로우 직후의 관계(커밋 뒤 새 트랜잭션에서 다시 읽는다 — 동시에 서로 팔로우했을 때 늦게 커밋한 쪽이 맞팔을 정확히 본다, QA P3-6).
     */
    @Transactional(readOnly = true)
    public Related relationTo(ExplorerId me, String handle) {
        return related(circleOf(me).relationTo(byHandle(handle)));
    }

    /** DELETE /friends/{handle} — 멱등: 없는 handle·숨은 대상·팔로우 안 함 모두 같은 결과(QA P3-3). */
    @Transactional
    public void unfollow(ExplorerId me, String handle) {
        territories.personalMapId(me.value());
        Followings followings = Followings.of(me, profiles.accountLinked(me.value()), friendships.outgoing(me));
        profiles.explorerIdByHandle(handle).map(ExplorerId::of).flatMap(followings::unfollow).ifPresent(friendships::remove);
    }

    /** GET /friends — 팔로잉 ∪ 팔로워(맞팔로우 표시). 한쪽으로만 팔로우하는 숨은 대상은 빠진다. */
    @Transactional(readOnly = true)
    public Friends friends(ExplorerId me) {
        territories.personalMapId(me.value());
        SocialCircle circle = circleOf(me);
        Set<ExplorerId> visible = audience.visibleAmong(circle.following().stream().map(ExplorerId::value).toList(), me.value())
            .stream().map(ExplorerId::of).collect(Collectors.toUnmodifiableSet());
        return new Friends(profiles.handleOf(me.value()).orElse(null), profiles.accountLinked(me.value()),
            circle.relations(visible).stream().map(this::related).toList(), circle.mutualCount());
    }

    private SocialCircle circleOf(ExplorerId me) {
        return SocialCircle.of(me, friendships.outgoing(me), friendships.incoming(me));
    }

    private ExplorerId byHandle(String handle) {
        return profiles.explorerIdByHandle(handle).map(ExplorerId::of).orElseThrow(SocialError.PROFILE_NOT_FOUND::exception);
    }

    private Related related(FriendRelation relation) {
        return new Related(relation, profiles.handleOf(relation.explorerId().value()).orElse(null));
    }

    /** 관계 + 그 사람의 지금 handle(계정이 사라졌으면 null). */
    public record Related(FriendRelation relation, String handle) {}

    /** @param myHandle 내 handle(익명이면 null) · @param loggedIn 팔로우할 수 있는지(계정 연결) */
    public record Friends(String myHandle, boolean loggedIn, List<Related> relations, int mutualCount) {
        public Friends {
            relations = List.copyOf(relations);
        }
    }
}
