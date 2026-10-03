package com.kobi.territory.social.domain.ranking;

import static com.kobi.territory.social.domain.Fixtures.KIM;
import static com.kobi.territory.social.domain.Fixtures.LEE;
import static com.kobi.territory.social.domain.Fixtures.ME;
import static com.kobi.territory.social.domain.Fixtures.PARK;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 5단계 리더 Q1(선점 방문이 이의 표시되면 다음 방문이 선점) · §5(지도 안 랭킹은 방문을 멤버별로 센다). */
@DisplayName("지도 랭킹")
class MapLeaderboardTest {

    /** 지도장 ME, 멤버 KIM·LEE(방문 없음), 떠난 PARK. */
    static final ExplorerId OWNER = ME;
    static final ExplorerId GUEST = KIM;
    static final ExplorerId QUIET = LEE;
    static final ExplorerId LEFT = PARK;

    static MapVisitFact visit(ExplorerId who, String code, Rarity rarity, boolean claim, boolean disputed) {
        return new MapVisitFact(who, code, rarity, claim ? 1 : 2, disputed);
    }

    @Nested
    @DisplayName("멤버들이 칠한 지도에서")
    class Standings {

        static final MapLeaderboard BOARD = MapLeaderboard.of(List.of(
            visit(OWNER, "KR-11010", Rarity.COMMON, true, false),
            visit(OWNER, "KR-11020", Rarity.COMMON, true, false),
            visit(GUEST, "KR-11010", Rarity.COMMON, false, false),
            visit(GUEST, "KR-37430", Rarity.LEGEND, true, false),
            visit(GUEST, "KR-26010", Rarity.RARE, true, true),
            visit(LEFT, "KR-11030", Rarity.COMMON, true, false)), List.of(OWNER, GUEST, QUIET));

        @Test
        @DisplayName("영토 수 → 선점 수 → 전설 수 순으로 순위를 매긴다")
        void ordersByTerritoryClaimLegend() {
            assertThat(BOARD.standings()).containsExactly(
                new MapStanding(OWNER, 2, 2, 0, 1),
                new MapStanding(GUEST, 2, 1, 1, 2),
                new MapStanding(QUIET, 0, 0, 0, 3));
        }

        @Test
        @DisplayName("이의가 걸린 방문은 영토에도 선점에도 세지 않고 뺀 수를 알려 준다")
        void excludesDisputed() {
            assertThat(BOARD.standings().get(1)).isEqualTo(new MapStanding(GUEST, 2, 1, 1, 2));
            assertThat(BOARD.disputedExcluded()).isEqualTo(1);
        }

        @Test
        @DisplayName("지도를 떠난 사람은 줄이 없다")
        void onlyCurrentMembers() {
            assertThat(BOARD.standings()).extracting(MapStanding::explorerId).doesNotContain(LEFT);
        }

        @Test
        @DisplayName("아직 칠하지 않은 멤버도 0곳으로 줄에 선다")
        void idleMemberListed() {
            assertThat(BOARD.standings()).contains(new MapStanding(QUIET, 0, 0, 0, 3));
        }
    }

    @Test
    @DisplayName("선점 방문에 이의가 걸리면 다음 방문이 선점이 된다")
    void disputedClaimPassesToNext() {
        MapLeaderboard board = MapLeaderboard.of(List.of(
            new MapVisitFact(GUEST, "KR-37020", Rarity.COMMON, 1, true),
            new MapVisitFact(OWNER, "KR-37020", Rarity.COMMON, 2, false),
            new MapVisitFact(QUIET, "KR-37020", Rarity.COMMON, 3, false)), List.of(OWNER, GUEST, QUIET));

        assertThat(board.standings()).containsExactly(
            new MapStanding(OWNER, 1, 1, 0, 1), new MapStanding(QUIET, 1, 0, 0, 2), new MapStanding(GUEST, 0, 0, 0, 3));
    }

    @Test
    @DisplayName("영토·선점·전설 수가 모두 같으면 같은 순위다")
    void tiesShareRank() {
        MapLeaderboard board = MapLeaderboard.of(List.of(
            visit(OWNER, "KR-11010", Rarity.COMMON, true, false),
            visit(GUEST, "KR-11020", Rarity.COMMON, true, false)), List.of(QUIET, GUEST, OWNER));

        assertThat(board.standings()).extracting(MapStanding::rank).containsExactly(1, 1, 3);
    }
}
