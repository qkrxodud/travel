package com.kobi.territory.sharing.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.sharing.domain.SharingError;
import java.time.Clock;
import java.time.Year;
import org.springframework.stereotype.Service;

/**
 * 공개 프로필(/u/{handle}) 유스케이스. 공개 범위가 보는 사람에게 열려 있을 때만(PUBLIC 은 누구나, FRIENDS 는 서로 팔로우한
 * 친구만 — 5단계, 그 밖엔 PROFILE_NOT_FOUND 존재 숨김). 보여 주는 것은 색칠·집계·월 단위 시기뿐이다(Showcase, §7).
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

    /** @param viewer 보는 사람(로그인 세션·토큰, 익명 방문자면 null) */
    public PublicProfile profile(String handle, ExplorerId viewer) {
        ExplorerId owner = profiles.explorerIdByHandle(handle).map(ExplorerId::of)
            .orElseThrow(SharingError.PROFILE_NOT_FOUND::exception);
        privacy.requireVisibleTo(owner, viewer);
        String canonical = profiles.handleOf(owner.value()).orElseThrow(SharingError.PROFILE_NOT_FOUND::exception);
        return new PublicProfile(showcases.read(owner.value(), canonical), territories.profileMapsOf(owner.value()),
            Year.now(clock));
    }
}
