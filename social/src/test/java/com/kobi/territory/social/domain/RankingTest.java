package com.kobi.territory.social.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.social.domain.ranking.FriendLeaderboard;
import com.kobi.territory.social.domain.ranking.FriendScore;
import com.kobi.territory.social.domain.ranking.FriendStanding;
import com.kobi.territory.social.domain.ranking.MapLeaderboard;
import com.kobi.territory.social.domain.ranking.MapStanding;
import com.kobi.territory.social.domain.ranking.MapVisitFact;
import java.util.List;
import org.junit.jupiter.api.Test;

/** D1: 지도 안 랭킹(이의 제외·지금 멤버만·동점), 친구 랭킹(중복 제거 지역 수·동점·본인 없음 보정·콜드 스타트 필요). */
class RankingTest {

    static final ExplorerId OWNER = ExplorerId.of("00000000-0000-0000-0000-00000000000a");
    static final ExplorerId GUEST = ExplorerId.of("00000000-0000-0000-0000-00000000000b");
    static final ExplorerId QUIET = ExplorerId.of("00000000-0000-0000-0000-00000000000c");
    static final ExplorerId LEFT = ExplorerId.of("00000000-0000-0000-0000-00000000000d");

    static MapVisitFact visit(ExplorerId who, String code, Rarity rarity, boolean claim, boolean disputed) {
        return new MapVisitFact(who, code, rarity, claim ? 1 : 2, disputed);
    }

    @Test
    void 지도_안_랭킹은_이의_방문을_빼고_영토_선점_전설_순으로_매긴다() {
        List<MapVisitFact> visits = List.of(
            visit(OWNER, "KR-11010", Rarity.COMMON, true, false),
            visit(OWNER, "KR-11020", Rarity.COMMON, true, false),
            visit(GUEST, "KR-11010", Rarity.COMMON, false, false),
            visit(GUEST, "KR-37430", Rarity.LEGEND, true, false),
            visit(GUEST, "KR-26010", Rarity.RARE, true, true),   // 이의 — 영토·선점 어디에도 안 센다
            visit(LEFT, "KR-11030", Rarity.COMMON, true, false)); // 멤버가 아님(목록 밖) — 줄 없음

        MapLeaderboard board = MapLeaderboard.of(visits, List.of(OWNER, GUEST, QUIET));

        assertThat(board.standings()).containsExactly(
            new MapStanding(OWNER, 2, 2, 0, 1),
            new MapStanding(GUEST, 2, 1, 1, 2),
            new MapStanding(QUIET, 0, 0, 0, 3));
        assertThat(board.disputedExcluded()).isEqualTo(1);
    }

    @Test
    void 선점_방문이_이의_표시되면_다음_이의_아닌_방문이_선점이다() {
        MapLeaderboard board = MapLeaderboard.of(List.of(
            new MapVisitFact(GUEST, "KR-37020", Rarity.COMMON, 1, true),   // 선점이지만 이의
            new MapVisitFact(OWNER, "KR-37020", Rarity.COMMON, 2, false),  // → 선점
            new MapVisitFact(QUIET, "KR-37020", Rarity.COMMON, 3, false)), List.of(OWNER, GUEST, QUIET));

        assertThat(board.standings()).containsExactly(
            new MapStanding(OWNER, 1, 1, 0, 1), new MapStanding(QUIET, 1, 0, 0, 2), new MapStanding(GUEST, 0, 0, 0, 3));
    }

    @Test
    void 지도_안_랭킹은_세_값이_같으면_같은_순위다() {
        MapLeaderboard board = MapLeaderboard.of(List.of(
            visit(OWNER, "KR-11010", Rarity.COMMON, true, false),
            visit(GUEST, "KR-11020", Rarity.COMMON, true, false)), List.of(QUIET, GUEST, OWNER));

        assertThat(board.standings()).extracting(MapStanding::rank).containsExactly(1, 1, 3);
    }

    @Test
    void 친구_랭킹은_지역_수로_순위를_매기고_같으면_같은_순위다() {
        FriendLeaderboard board = FriendLeaderboard.of(OWNER, List.of(
            new FriendScore(GUEST, 12, 4), new FriendScore(OWNER, 12, 3), new FriendScore(QUIET, 30, 9)));

        assertThat(board.standings()).extracting(FriendStanding::explorerId).containsExactly(QUIET, GUEST, OWNER);
        assertThat(board.standings()).extracting(FriendStanding::rank).containsExactly(1, 2, 2);
        assertThat(board.mine().me()).isTrue();
        assertThat(board.friendCount()).isEqualTo(2);
        assertThat(board.needsBaseline()).isFalse();
    }

    @Test
    void 친구가_없으면_본인_한_줄이고_콜드_스타트_비교가_필요하다() {
        FriendLeaderboard board = FriendLeaderboard.of(OWNER, List.of());

        assertThat(board.standings()).containsExactly(new FriendStanding(OWNER, 0, 1, 1, true));
        assertThat(board.needsBaseline()).isTrue();
    }
}
