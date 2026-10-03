package com.kobi.territory.sharing.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.sharing.domain.SharingError;
import java.time.Clock;
import java.time.Year;
import org.springframework.stereotype.Service;

/**
 * 공개 프로필(/u/{handle}) 유스케이스. 공개 범위가 PUBLIC 일 때만(PRIVATE·FRIENDS → PROFILE_NOT_FOUND, 존재 숨김 — FRIENDS 는
 * 5단계 친구 기능 전까지 PRIVATE 처럼). 보여 주는 것은 색칠·집계·월 단위 시기뿐이다(Showcase, §7).
 */
@Service
public class PublicProfileService {

    private final ExplorerProfileQuery profiles;
    private final PrivacyService privacy;
    private final ShowcaseReader showcases;
    private final TerritoryQuery territories;
    private final Clock clock;

    public PublicProfileService(ExplorerProfileQuery profiles, PrivacyService privacy, ShowcaseReader showcases,
                                TerritoryQuery territories, Clock clock) {
        this.profiles = profiles;
        this.privacy = privacy;
        this.showcases = showcases;
        this.territories = territories;
        this.clock = clock;
    }

    public PublicProfile profile(String handle) {
        ExplorerId owner = profiles.explorerIdByHandle(handle).map(ExplorerId::of)
            .orElseThrow(SharingError.PROFILE_NOT_FOUND::exception);
        privacy.requireVisibleToPublic(owner);
        String canonical = profiles.handleOf(owner.value()).orElseThrow(SharingError.PROFILE_NOT_FOUND::exception);
        return new PublicProfile(showcases.read(owner.value(), canonical), territories.profileMapsOf(owner.value()),
            Year.now(clock));
    }
}
