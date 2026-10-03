package com.kobi.territory.config;

import com.kobi.territory.sharing.api.query.ProfileVisibilityQuery;
import com.kobi.territory.social.application.ProfileAudience;
import java.util.Collection;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 조립(5단계): 소셜의 공개 범위 포트 ← 공유의 공개 범위 Query. 소셜·공유는 서로를 직접 참조하지 않는다(공유의 FRIENDS 판정이 소셜의
 * 맞팔로우를 물어 순환이 되므로) — app-api 만 잇는다. 도메인 로직 없음(전달만).
 */
@Component
class ProfileAudienceAdapter implements ProfileAudience {

    private final ProfileVisibilityQuery profileVisibility;

    ProfileAudienceAdapter(ProfileVisibilityQuery profileVisibility) {
        this.profileVisibility = profileVisibility;
    }

    @Override
    public boolean visibleTo(String ownerId, String viewerId) {
        return profileVisibility.visibleTo(ownerId, viewerId);
    }

    @Override
    public Set<String> visibleAmong(Collection<String> ownerIds, String viewerId) {
        return profileVisibility.visibleAmong(ownerIds, viewerId);
    }
}
