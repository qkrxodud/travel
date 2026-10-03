package com.kobi.territory.social.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.TerritoryComparison;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.progression.api.query.ExplorerRegionQuery;
import com.kobi.territory.social.domain.SocialError;
import com.kobi.territory.social.domain.friendship.FriendshipRepository;
import com.kobi.territory.social.domain.friendship.SocialCircle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 영토 비교(GET /compare/{handle}, VS): 두 탐험가의 탐험가 단위 활성 지역(explorer_region — 친구 랭킹과 같은 기준)으로 나만·둘 다·
 * 상대만. 계산은 4단계 VS 카드와 같은 공유 커널 함수(TerritoryComparison). 대상이 나와 맞팔로우이거나 그의 프로필이 나에게 보일 때만
 * (SocialCircle 이 판단, 아니면 404 PROFILE_NOT_FOUND).
 */
@Service
public class CompareService {

    private final FriendshipRepository friendships;
    private final ExplorerRegionQuery regions;
    private final ExplorerProfileQuery profiles;
    private final TerritoryQuery territories;
    private final ProfileAudience audience;

    public CompareService(FriendshipRepository friendships, ExplorerRegionQuery regions, ExplorerProfileQuery profiles,
                          TerritoryQuery territories, ProfileAudience audience) {
        this.friendships = friendships;
        this.regions = regions;
        this.profiles = profiles;
        this.territories = territories;
        this.audience = audience;
    }

    @Transactional(readOnly = true)
    public Comparison compare(ExplorerId me, String handle) {
        territories.personalMapId(me.value());
        ExplorerId target = profiles.explorerIdByHandle(handle).map(ExplorerId::of)
            .orElseThrow(SocialError.PROFILE_NOT_FOUND::exception);
        SocialCircle circle = SocialCircle.of(me, friendships.outgoing(me), friendships.incoming(me));
        circle.requireComparableWith(target, audience.visibleTo(target.value(), me.value()));
        TerritoryComparison comparison = TerritoryComparison.of(regions.activeRegionCodesOf(me.value()),
            regions.activeRegionCodesOf(target.value()));
        return new Comparison(profiles.handleOf(me.value()).orElse(null), profiles.handleOf(target.value()).orElseThrow(),
            circle.isMutual(target), comparison);
    }

    /** @param myHandle 내 handle(익명이면 null) · @param mutual 맞팔로우 친구인지 */
    public record Comparison(String myHandle, String otherHandle, boolean mutual, TerritoryComparison comparison) {}
}
