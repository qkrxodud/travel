package com.kobi.territory.social.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.progression.api.query.ExplorerRegionQuery;
import com.kobi.territory.progression.api.query.ProgressQuery;
import com.kobi.territory.progression.api.query.ProgressSummaryView;
import com.kobi.territory.social.domain.friendship.FriendshipRepository;
import com.kobi.territory.social.domain.friendship.SocialCircle;
import com.kobi.territory.social.domain.ranking.FriendLeaderboard;
import com.kobi.territory.social.domain.ranking.FriendScore;
import com.kobi.territory.social.domain.ranking.FriendStanding;
import com.kobi.territory.social.domain.ranking.MapLeaderboard;
import com.kobi.territory.social.domain.ranking.MapStanding;
import com.kobi.territory.social.domain.ranking.MapVisitFact;
import com.kobi.territory.social.domain.stats.ProvinceStat;
import com.kobi.territory.social.domain.stats.ProvinceStats;
import com.kobi.territory.social.domain.stats.ProvinceTallies;
import com.kobi.territory.social.domain.stats.ProvinceTally;
import com.kobi.territory.social.domain.stats.RankPercentile;
import com.kobi.territory.social.domain.stats.RankSnapshotRepository;
import com.kobi.territory.social.domain.stats.RegionStat;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 랭킹 조회(§5 랭킹 두 층):
 * <ul>
 *   <li>친구 랭킹 — 요청 시점 조인 계산: 나 + 프로필이 나에게 보이는 맞팔로우 친구(PRIVATE 친구 제외)의 탐험가 단위 중복 제거 지역 수(explorer_region)·레벨. 친구가 없으면
 *       "내 지역 평균 유저"(일 1회 배치 province_stats)와 비교(콜드 스타트 §7)</li>
 *   <li>지도 안 랭킹 — 그 지도의 visit 을 멤버별로(영토·선점·전설), 이의·숨김 제외(탐험 공개 Query)</li>
 *   <li>전체 — 순위표 없이 상위 %만(일 1회 배치 rank_percentile, §7 참고용)</li>
 * </ul>
 * 배치 스냅숏(상위 %·지역 통계)은 애플리케이션 캐시(설정 TTL)로 낸다 — 배치가 끝나면 비운다.
 */
@Service
public class RankingService {

    private static final String ALL = "all";
    /** 탐험가별 상위 % 캐시 상한(넘으면 만료 항목 정리 → 그래도 넘으면 비움). */
    private static final int PERCENTILE_CACHE_LIMIT = 10_000;

    private final FriendshipRepository friendships;
    private final ExplorerRegionQuery regions;
    private final ProgressQuery progresses;
    private final TerritoryQuery territories;
    private final ExplorerProfileQuery profiles;
    private final RankSnapshotRepository snapshots;
    private final ProfileAudience audience;
    private final ExpiringCache<ExplorerId, Optional<RankPercentile>> percentiles;
    private final ExpiringCache<String, List<RegionStat>> regionStats;
    private final ExpiringCache<String, ProvinceStats> provinceStats;

    public RankingService(FriendshipRepository friendships, ExplorerRegionQuery regions, ProgressQuery progresses,
                          TerritoryQuery territories, ExplorerProfileQuery profiles, RankSnapshotRepository snapshots,
                          ProfileAudience audience, SocialSettings settings, Clock clock) {
        this.audience = audience;
        this.friendships = friendships;
        this.regions = regions;
        this.progresses = progresses;
        this.territories = territories;
        this.profiles = profiles;
        this.snapshots = snapshots;
        this.percentiles = new ExpiringCache<>(settings.statsCacheTtl(), clock, PERCENTILE_CACHE_LIMIT);
        this.regionStats = new ExpiringCache<>(settings.statsCacheTtl(), clock, 1);
        this.provinceStats = new ExpiringCache<>(settings.statsCacheTtl(), clock, 1);
    }

    /** GET /rankings/friends — 익명도 본인 줄 + 지역 평균은 본다(팔로우는 로그인 뒤). */
    @Transactional(readOnly = true)
    public FriendRanking friends(ExplorerId me) {
        territories.personalMapId(me.value());
        SocialCircle circle = SocialCircle.of(me, friendships.outgoing(me), friendships.incoming(me));
        Set<ExplorerId> visible = audience.visibleAmong(circle.mutualFriends().stream().map(ExplorerId::value).toList(), me.value())
            .stream().map(ExplorerId::of).collect(Collectors.toUnmodifiableSet());
        List<ExplorerId> members = circle.rankingMembers(visible); // PRIVATE 맞팔 친구는 숨김(리더 결정 1), 본인은 항상
        ProvinceTallies tallies = talliesOf(members);
        Map<ExplorerId, ProgressSummaryView> progress = members.stream()
            .collect(Collectors.toMap(Function.identity(), member -> progresses.summaryOf(member.value())));
        FriendLeaderboard board = FriendLeaderboard.of(me, members.stream()
            .map(member -> new FriendScore(member, tallies.regionCountOf(member), progress.get(member).level())).toList());
        List<FriendRow> rows = board.standings().stream()
            .map(standing -> new FriendRow(standing, profiles.handleOf(standing.explorerId().value()).orElse(null),
                progress.get(standing.explorerId()).titleName()))
            .toList();
        Optional<String> mainProvince = tallies.mainProvinceOf(me);
        return new FriendRanking(profiles.accountLinked(me.value()), rows, board.friendCount(), mainProvince.orElse(null),
            provinceStats().coldStartBaseline(board.friendCount(), mainProvince).orElse(null));
    }

    /** GET /rankings/maps/{mapId} — 멤버만(아니면 403 NOT_A_MEMBER, 지도 없음 404). */
    @Transactional(readOnly = true)
    public MapRanking map(ExplorerId me, String mapId) {
        String resolved = territories.resolveMapId(me.value(), mapId);
        List<MapVisitFact> visits = territories.mapVisits(resolved).stream()
            .map(visit -> new MapVisitFact(ExplorerId.of(visit.explorerId()), visit.regionCode(), visit.rarity(), visit.claimOrder(),
                visit.disputed()))
            .toList();
        MapLeaderboard board = MapLeaderboard.of(visits, territories.memberIdsOf(resolved).stream().map(ExplorerId::of).toList());
        return new MapRanking(resolved, board.standings().stream()
            .map(standing -> new MapRow(standing, profiles.handleOf(standing.explorerId().value()).orElse(null),
                standing.explorerId().equals(me)))
            .toList(), board.disputedExcluded());
    }

    /** GET /rankings/me/percentile — 배치 전이거나 지역 0곳이면 빈 값. */
    public Optional<RankPercentile> percentile(ExplorerId me) {
        territories.personalMapId(me.value());
        return percentiles.get(me, () -> snapshots.findPercentile(me));
    }

    /** GET /catalog/region-stats — 지역별 방문자 비율(배치 스냅숏). */
    public List<RegionStat> regionStats() {
        return regionStats.get(ALL, snapshots::regionStats);
    }

    /** 배치가 스냅숏을 바꾼 뒤 캐시를 비운다. */
    public void invalidateStats() {
        percentiles.clear();
        regionStats.clear();
        provinceStats.clear();
    }

    private ProvinceStats provinceStats() {
        return provinceStats.get(ALL, () -> ProvinceStats.of(snapshots.provinceStats()));
    }

    private ProvinceTallies talliesOf(List<ExplorerId> members) {
        return ProvinceTallies.of(regions.provinceTalliesOf(members.stream().map(ExplorerId::value).toList()).stream()
            .map(tally -> new ProvinceTally(ExplorerId.of(tally.explorerId()), tally.provinceCode(), tally.regionCount()))
            .toList());
    }

    /** 친구 랭킹 한 줄 + handle(익명 본인이면 null) + 보이는 칭호. */
    public record FriendRow(FriendStanding standing, String handle, String titleName) {}

    /**
     * @param loggedIn     팔로우할 수 있는지(아니면 화면이 로그인 유도)
     * @param friendCount  맞팔로우 친구 수
     * @param mainProvince 내 주 활동 시·도(지역 0곳이면 null)
     * @param baseline     친구가 없을 때 비교할 지역 평균 유저(배치 전이면 null)
     */
    public record FriendRanking(boolean loggedIn, List<FriendRow> rows, int friendCount, String mainProvince,
                                ProvinceStat baseline) {
        public FriendRanking {
            rows = List.copyOf(rows);
        }
    }

    public record MapRow(MapStanding standing, String handle, boolean me) {}

    public record MapRanking(String mapId, List<MapRow> rows, int disputedExcluded) {
        public MapRanking {
            rows = List.copyOf(rows);
        }
    }
}
