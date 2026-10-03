package com.kobi.territory.social.api.web;

import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.api.web.SocialDtos.FriendResponse;
import com.kobi.territory.social.api.web.SocialDtos.FriendsResponse;
import com.kobi.territory.social.application.FriendshipService;
import com.kobi.territory.social.domain.SocialError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 친구(팔로우). 인증 = @CurrentExplorer(로그인 세션 → 토큰). 팔로우는 계정 연결된 탐험가만(익명 401 LOGIN_REQUIRED), 대상은 handle 로
 * (없으면 404 PROFILE_NOT_FOUND). "친구" = 서로 팔로우.
 * <ul>
 *   <li>{@code POST /friends/{handle}} — 201 관계 / 401 LOGIN_REQUIRED / 404 PROFILE_NOT_FOUND(없는 handle, 또는 숨은 프로필 —
 *       PRIVATE·친구 아닌 FRIENDS 이면서 나를 팔로우하지 않음: 팔로우는 기록되고 그가 나를 팔로우하면 친구가 된다) / 409 ALREADY_FOLLOWING(보이는
 *       대상만) / 422 CANNOT_FOLLOW_SELF</li>
 *   <li>{@code DELETE /friends/{handle}} — 204(멱등 — 없는 handle·팔로우 안 함도 204, QA P3-3)</li>
 *   <li>{@code GET /friends} — 팔로잉 ∪ 팔로워(맞팔 표시)</li>
 * </ul>
 */
@RestController
public class FriendController {

    private final FriendshipService friendships;

    public FriendController(FriendshipService friendships) {
        this.friendships = friendships;
    }

    @PostMapping("/friends/{handle}")
    public ResponseEntity<FriendResponse> follow(@CurrentExplorer ExplorerId explorerId, @PathVariable("handle") String handle) {
        // 숨은 대상은 팔로우를 기록하되 없는 handle 과 같은 404(존재 숨김). 관계는 커밋 뒤 다시 읽는다(동시 맞팔 — QA P3-6)
        if (!friendships.follow(explorerId, handle)) throw SocialError.PROFILE_NOT_FOUND.exception();
        return ResponseEntity.status(HttpStatus.CREATED).body(FriendResponse.of(friendships.relationTo(explorerId, handle)));
    }

    @DeleteMapping("/friends/{handle}")
    public ResponseEntity<Void> unfollow(@CurrentExplorer ExplorerId explorerId, @PathVariable("handle") String handle) {
        friendships.unfollow(explorerId, handle);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/friends")
    public FriendsResponse friends(@CurrentExplorer ExplorerId explorerId) {
        return FriendsResponse.of(friendships.friends(explorerId));
    }
}
