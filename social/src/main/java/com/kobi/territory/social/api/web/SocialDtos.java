package com.kobi.territory.social.api.web;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.TerritoryComparison;
import com.kobi.territory.social.application.CompareService.Comparison;
import com.kobi.territory.social.application.FeedService.Feed;
import com.kobi.territory.social.application.FeedService.FeedItem;
import com.kobi.territory.social.application.FriendshipService.Friends;
import com.kobi.territory.social.application.FriendshipService.Related;
import com.kobi.territory.social.application.RankingService.FriendRanking;
import com.kobi.territory.social.application.RankingService.FriendRow;
import com.kobi.territory.social.application.RankingService.MapRanking;
import com.kobi.territory.social.application.RankingService.MapRow;
import com.kobi.territory.social.domain.stats.ProvinceStat;
import com.kobi.territory.social.domain.stats.RankPercentile;
import com.kobi.territory.social.domain.stats.RegionStat;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 소셜 웹 DTO(5단계). 정확한 시각·메모·사진은 싣지 않는다 — 피드는 상대 시각(일 단위)만. */
public final class SocialDtos {

    private SocialDtos() {}

    /** 나와 한 사람의 관계(explorerId 는 싣지 않는다 — QA P3-3). */
    public record FriendResponse(String handle, boolean following, boolean follower, boolean mutual) {
        static FriendResponse of(Related related) {
            return new FriendResponse(related.handle(), related.relation().following(),
                related.relation().follower(), related.relation().mutual());
        }
    }

    /** GET /friends — 팔로잉 ∪ 팔로워(친구 = 맞팔 먼저). loggedIn=false 면 화면이 로그인 유도. */
    public record FriendsResponse(String handle, boolean loggedIn, int mutualCount, List<FriendResponse> people) {
        static FriendsResponse of(Friends friends) {
            return new FriendsResponse(friends.myHandle(), friends.loggedIn(), friends.mutualCount(),
                friends.relations().stream().map(FriendResponse::of).toList());
        }
    }

    /**
     * 친구 소식 한 건. 종류별로 쓰는 값만 채운다(VISIT = regionCode·rarity, THEME_COMPLETED = themeId, LEVEL_UP = level,
     * BADGE_EARNED = badgeId). when = 상대 시각("오늘"·"어제"·"3일 전"…).
     */
    public record FeedItemResponse(String handle, String kind, String regionCode, Rarity rarity, String themeId, Integer level,
                                   String badgeId, int daysAgo, String when) {
        static FeedItemResponse of(FeedItem item) {
            var detail = item.entry().detail();
            return new FeedItemResponse(item.handle(), item.entry().kind().name(), detail.regionCode(), detail.rarity(),
                detail.themeId(), detail.level(), detail.badgeId(), item.age().daysAgo(), item.age().label());
        }
    }

    public record FeedResponse(boolean loggedIn, int followingCount, List<FeedItemResponse> items) {
        static FeedResponse of(Feed feed) {
            return new FeedResponse(feed.loggedIn(), feed.followingCount(), feed.items().stream().map(FeedItemResponse::of).toList());
        }
    }

    public record FriendRankRow(String explorerId, String handle, boolean me, int rank, int regionCount, int level,
                                String titleName) {
        static FriendRankRow of(FriendRow row) {
            return new FriendRankRow(row.standing().explorerId().value(), row.handle(), row.standing().me(), row.standing().rank(),
                row.standing().regionCount(), row.standing().level(), row.titleName());
        }
    }

    /** 콜드 스타트 비교 대상(친구 0명): 내 주 활동 시·도의 평균 유저, 없으면 전국 평균(nationwide=true). */
    public record BaselineResponse(String provinceCode, boolean nationwide, int explorerCount, double averageRegionCount,
                                   Instant computedAt) {
        static BaselineResponse of(ProvinceStat stat) {
            return stat == null ? null : new BaselineResponse(stat.provinceCode(), stat.nationwide(), stat.explorerCount(),
                stat.averageRegionCount(), stat.computedAt());
        }
    }

    public record FriendRankingResponse(boolean loggedIn, int friendCount, String mainProvince, List<FriendRankRow> rows,
                                        BaselineResponse baseline) {
        static FriendRankingResponse of(FriendRanking ranking) {
            return new FriendRankingResponse(ranking.loggedIn(), ranking.friendCount(), ranking.mainProvince(),
                ranking.rows().stream().map(FriendRankRow::of).toList(), BaselineResponse.of(ranking.baseline()));
        }
    }

    public record MapRankRow(String explorerId, String handle, boolean me, int rank, int territories, int claims, int legends) {
        static MapRankRow of(MapRow row) {
            return new MapRankRow(row.standing().explorerId().value(), row.handle(), row.me(), row.standing().rank(),
                row.standing().territories(), row.standing().claims(), row.standing().legends());
        }
    }

    /** 지도 안 랭킹(이의 방문·탈퇴 유예 숨김 제외). disputedExcluded = 이의로 뺀 방문 수. */
    public record MapRankingResponse(String mapId, int disputedExcluded, List<MapRankRow> rows) {
        static MapRankingResponse of(MapRanking ranking) {
            return new MapRankingResponse(ranking.mapId(), ranking.disputedExcluded(),
                ranking.rows().stream().map(MapRankRow::of).toList());
        }
    }

    /** 전체 유저 중 상위 %(일 1회 배치). computed=false = 배치 전이거나 아직 지역이 없음. */
    public record PercentileResponse(boolean computed, Integer topPercent, Integer rank, Integer population, Integer regionCount,
                                     Instant computedAt) {
        static PercentileResponse of(Optional<RankPercentile> percentile) {
            return percentile.map(value -> new PercentileResponse(true, value.topPercent(), value.rank(), value.population(),
                    value.regionCount(), value.computedAt()))
                .orElseGet(() -> new PercentileResponse(false, null, null, null, null, null));
        }
    }

    public record Side(String handle, int regionCount) {}

    /** 영토 비교(VS): 나만 · 둘 다 · 상대만(지역 코드 KR-xxxxx, 코드 순). lead = 내 지역 수 − 상대 지역 수. */
    public record CompareResponse(Side me, Side other, boolean mutual, List<String> onlyMine, List<String> both,
                                  List<String> onlyTheirs, int lead) {
        static CompareResponse of(Comparison result) {
            TerritoryComparison comparison = result.comparison();
            return new CompareResponse(new Side(result.myHandle(), comparison.mineCount()),
                new Side(result.otherHandle(), comparison.theirsCount()), result.mutual(), comparison.onlyMine(), comparison.both(),
                comparison.onlyTheirs(), comparison.lead());
        }
    }

    public record RegionStatResponse(String regionCode, int visitorCount, double visitorPercent) {}

    /** 지역별 방문자 비율(배치 스냅숏). population·computedAt 은 배치 전이면 0·null. */
    public record RegionStatsResponse(int population, Instant computedAt, List<RegionStatResponse> regions) {
        static RegionStatsResponse of(List<RegionStat> stats) {
            Optional<RegionStat> any = stats.stream().max(Comparator.comparing(RegionStat::computedAt));
            return new RegionStatsResponse(any.map(RegionStat::population).orElse(0), any.map(RegionStat::computedAt).orElse(null),
                stats.stream().map(stat -> new RegionStatResponse(stat.regionCode(), stat.visitorCount(), stat.visitorPercent()))
                    .toList());
        }
    }
}
