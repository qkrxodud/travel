package com.kobi.territory.config;

import com.kobi.territory.sharing.application.FriendDirectory;
import com.kobi.territory.social.api.query.FriendshipQuery;
import org.springframework.stereotype.Component;

/**
 * 조립(5단계): 공유의 친구 관계 포트 ← 소셜의 맞팔로우 Query. 공개 범위 FRIENDS(프로필·카드·VS·프로필 합류)가 실제로 동작하게 한다.
 * 도메인 로직 없음(전달만).
 */
@Component
class FriendDirectoryAdapter implements FriendDirectory {

    private final FriendshipQuery friendships;

    FriendDirectoryAdapter(FriendshipQuery friendships) {
        this.friendships = friendships;
    }

    @Override
    public boolean mutualFriends(String explorerId, String otherExplorerId) {
        return friendships.mutualFriends(explorerId, otherExplorerId);
    }
}
