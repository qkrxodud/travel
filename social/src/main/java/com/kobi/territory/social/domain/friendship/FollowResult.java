package com.kobi.territory.social.domain.friendship;

import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.social.domain.SocialError;
import java.util.Optional;

/**
 * 팔로우 커맨드 결과(QA P3-3). revealed = 대상의 존재를 응답으로 알려도 되는지 — 프로필이 나에게 보이거나 그가 이미 나를 팔로우할 때만.
 * 숨은 대상(PRIVATE·친구 아닌 FRIENDS)은 팔로우는 기록하되(그가 나를 팔로우하면 맞팔 = 친구가 된다) 응답은 없는 handle 과 같은 404다.
 *
 * @param started 새로 시작한 관계(이미 팔로우 중인 숨은 대상이면 빈 값 — 409 로 존재를 알리지 않는다)
 */
public record FollowResult(Optional<Friendship> started, boolean revealed) {

    /**
     * 저장 경합(같은 쌍을 동시에 팔로우해 PK 위반)일 때 낼 오류 — 보이는 대상은 ALREADY_FOLLOWING, 숨은 대상은 없는 handle 과 같은
     * PROFILE_NOT_FOUND(QA r2 P2-A — 동시 요청으로 존재를 가르지 않게).
     */
    public TerritoryException duplicateError() {
        return revealed ? SocialError.ALREADY_FOLLOWING.exception() : SocialError.PROFILE_NOT_FOUND.exception();
    }
}
