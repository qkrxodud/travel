package com.kobi.territory.social.api.web;

import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.api.web.SocialDtos.CompareResponse;
import com.kobi.territory.social.api.web.SocialDtos.FriendRankingResponse;
import com.kobi.territory.social.api.web.SocialDtos.MapRankingResponse;
import com.kobi.territory.social.api.web.SocialDtos.PercentileResponse;
import com.kobi.territory.social.api.web.SocialDtos.RegionStatsResponse;
import com.kobi.territory.social.application.CompareService;
import com.kobi.territory.social.application.RankingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 랭킹·비교(§5 랭킹 두 층 + 상위 %).
 * <ul>
 *   <li>{@code GET /rankings/friends} — 나 + 맞팔로우 친구(탐험가 단위 중복 제거 지역 수·레벨), 친구 0명이면 지역 평균 유저(baseline)</li>
 *   <li>{@code GET /rankings/maps/{mapId}} — 지도 안 랭킹(영토·선점·전설, 이의·숨김 제외). 멤버 아님 403 NOT_A_MEMBER</li>
 *   <li>{@code GET /rankings/me/percentile} — 전체 유저 중 상위 %(일 1회 배치, 참고용)</li>
 *   <li>{@code GET /compare/{handle}} — 영토 비교(나만·둘 다·상대만). 대상이 맞팔로우이거나 공개 프로필일 때만(아니면 404)</li>
 *   <li>{@code GET /catalog/region-stats} — 지역별 방문자 비율(일 1회 배치)</li>
 * </ul>
 */
@RestController
public class RankingController {

    private final RankingService rankings;
    private final CompareService comparisons;

    public RankingController(RankingService rankings, CompareService comparisons) {
        this.rankings = rankings;
        this.comparisons = comparisons;
    }

    @GetMapping("/rankings/friends")
    public FriendRankingResponse friends(@CurrentExplorer ExplorerId explorerId) {
        return FriendRankingResponse.of(rankings.friends(explorerId));
    }

    @GetMapping("/rankings/maps/{mapId}")
    public MapRankingResponse map(@CurrentExplorer ExplorerId explorerId, @PathVariable("mapId") String mapId) {
        return MapRankingResponse.of(rankings.map(explorerId, mapId));
    }

    @GetMapping("/rankings/me/percentile")
    public PercentileResponse percentile(@CurrentExplorer ExplorerId explorerId) {
        return PercentileResponse.of(rankings.percentile(explorerId));
    }

    @GetMapping("/compare/{handle}")
    public CompareResponse compare(@CurrentExplorer ExplorerId explorerId, @PathVariable("handle") String handle) {
        return CompareResponse.of(comparisons.compare(explorerId, handle));
    }

    @GetMapping("/catalog/region-stats")
    public RegionStatsResponse regionStats() {
        return RegionStatsResponse.of(rankings.regionStats());
    }
}
