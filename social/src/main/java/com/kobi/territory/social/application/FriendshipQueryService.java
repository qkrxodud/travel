package com.kobi.territory.social.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.api.query.FriendshipQuery;
import com.kobi.territory.social.domain.friendship.FriendshipRepository;
import com.kobi.territory.social.domain.friendship.SocialCircle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link FriendshipQuery} 구현 — 맞팔로우 판정(공유의 FRIENDS 공개 범위가 조립 모듈을 거쳐 쓴다). 이 빈은 공개 범위 포트
 * (ProfileAudience)에 의존하지 않는다 — 공유 → 소셜 → 공유 빈 순환을 만들지 않게.
 */
@Service
public class FriendshipQueryService implements FriendshipQuery {

    private final FriendshipRepository friendships;

    public FriendshipQueryService(FriendshipRepository friendships) {
        this.friendships = friendships;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean mutualFriends(String explorerId, String otherExplorerId) {
        ExplorerId one = ExplorerId.of(explorerId);
        ExplorerId other = ExplorerId.of(otherExplorerId);
        return SocialCircle.mutual(one, other, friendships.between(one, other));
    }
}
