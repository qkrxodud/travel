package com.kobi.territory.config;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.application.ProfileJoinGate;
import com.kobi.territory.sharing.api.query.ProfileVisibilityQuery;
import org.springframework.stereotype.Component;

/**
 * 조립: 탐험의 프로필 합류 문지기 포트 ← 공유의 공개 범위 Query(4단계 QA P3-5). 탐험·공유는 서로를 모르고(의존 매트릭스) app-api 만
 * 둘을 잇는다 — 도메인 로직은 없다(전달만).
 */
@Component
class ProfileJoinGateAdapter implements ProfileJoinGate {

    private final ProfileVisibilityQuery profileVisibility;

    ProfileJoinGateAdapter(ProfileVisibilityQuery profileVisibility) {
        this.profileVisibility = profileVisibility;
    }

    @Override
    public boolean profileOpen(ExplorerId profileOwner) {
        return profileVisibility.visibleToPublic(profileOwner.value());
    }
}
