package com.kobi.territory.catalog.domain.mystery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.catalog.domain.region.Region;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 이번 주 미스터리 지역 고르기: 희귀·전설 현행 지역 중 방문자 비율이 낮은 쪽(하위 구간)에서 주차와 서버 비밀값으로 결정적으로 한 곳.
 * 미니 지역: 희귀 넷(가평·양평·단양·영월), 전설 하나(울릉), 일반 하나(종로), 폐지된 희귀 하나(옛군).
 */
@DisplayName("미스터리 지역 고르기")
class MysteryDrawTest {

    private static final LocalDate 월요일 = LocalDate.of(2026, 10, 5);
    private static final MysteryRules 희귀전설_하위절반 = new MysteryRules(Set.of(Rarity.RARE, Rarity.LEGEND), 0.5);
    private static final MysterySeed 비밀값 = new MysterySeed("test-salt");

    private static Region 지역(String code, Rarity rarity) {
        return new Region(RegionCode.of(code), code, "KR-" + code.substring(3, 5), rarity, "KR", 1, null, null);
    }

    private static final Region 종로 = 지역("KR-11010", Rarity.COMMON);
    private static final Region 가평 = 지역("KR-31370", Rarity.RARE);
    private static final Region 양평 = 지역("KR-31380", Rarity.RARE);
    private static final Region 단양 = 지역("KR-33380", Rarity.RARE);
    private static final Region 영월 = 지역("KR-32380", Rarity.RARE);
    private static final Region 울릉 = 지역("KR-37430", Rarity.LEGEND);
    private static final Region 옛군 = new Region(RegionCode.of("KR-31990"), "옛군", "KR-31", Rarity.RARE, "KR", 1, null,
        LocalDate.of(2020, 1, 1));
    private static final List<Region> 전부 = List.of(종로, 가평, 양평, 단양, 영월, 울릉, 옛군);

    /** 방문자 비율: 가평 50%·양평 40%·단양 30%·영월 0%·울릉 10%·종로 90%. */
    private static final VisitorShares 통계 = VisitorShares.of(List.of(
        new RegionVisitors(가평.code(), 5, 10), new RegionVisitors(양평.code(), 4, 10), new RegionVisitors(단양.code(), 3, 10),
        new RegionVisitors(울릉.code(), 1, 10), new RegionVisitors(종로.code(), 9, 10)));

    @Nested
    @DisplayName("후보는")
    class Candidates {

        @Test
        @DisplayName("희귀·전설 현행 지역 중 방문자 비율이 낮은 쪽 절반이다")
        void bottomHalf() {
            assertThat(MysteryDraw.candidates(월요일, 전부, 통계, 희귀전설_하위절반, 비밀값)).containsExactly(영월, 울릉, 단양);
        }

        @Test
        @DisplayName("일반 지역과 폐지된 지역은 후보가 아니다")
        void excludesCommonAndRetired() {
            assertThat(MysteryDraw.candidates(월요일, 전부, VisitorShares.none(), new MysteryRules(Set.of(Rarity.RARE, Rarity.LEGEND), 1), 비밀값))
                .doesNotContain(종로, 옛군).hasSize(5);
        }

        @Test
        @DisplayName("방문자 비율이 같은 곳끼리는 주마다 다른 순서로 선다")
        void tiesShuffledByWeek() {
            List<List<Region>> orders = IntStream.range(0, 10).mapToObj(week -> MysteryDraw.candidates(월요일.plusWeeks(week), 전부,
                VisitorShares.none(), new MysteryRules(Set.of(Rarity.RARE, Rarity.LEGEND), 1), 비밀값)).toList();

            assertThat(new HashSet<>(orders)).hasSizeGreaterThan(1);
        }

        @Test
        @DisplayName("하위 구간이 아무리 좁아도 한 곳은 남는다")
        void atLeastOne() {
            assertThat(MysteryDraw.candidates(월요일, 전부, 통계, new MysteryRules(Set.of(Rarity.LEGEND), 0.01), 비밀값)).containsExactly(울릉);
        }
    }

    @Nested
    @DisplayName("고른 지역은")
    class Draw {

        @Test
        @DisplayName("같은 주면 언제 몇 번을 골라도 같다")
        void deterministic() {
            MysteryWeek first = MysteryDraw.draw(월요일, 전부, 통계, 희귀전설_하위절반, 비밀값, Instant.EPOCH);
            MysteryWeek again = MysteryDraw.draw(월요일, 전부, 통계, 희귀전설_하위절반, 비밀값, Instant.parse("2026-10-09T00:00:00Z"));

            assertThat(again.region()).isEqualTo(first.region());
        }

        @Test
        @DisplayName("언제나 하위 구간 후보 중 하나다")
        void withinCandidates() {
            Set<RegionCode> candidates = Set.of(영월.code(), 울릉.code(), 단양.code());

            IntStream.range(0, 30).forEach(week -> assertThat(
                MysteryDraw.draw(월요일.plusWeeks(week), 전부, 통계, 희귀전설_하위절반, 비밀값, Instant.EPOCH).region()).isIn(candidates));
        }

        @Test
        @DisplayName("주가 바뀌면 다른 후보가 골라지기도 한다")
        void variesByWeek() {
            Set<RegionCode> picked = new HashSet<>();
            IntStream.range(0, 30).forEach(week ->
                picked.add(MysteryDraw.draw(월요일.plusWeeks(week), 전부, 통계, 희귀전설_하위절반, 비밀값, Instant.EPOCH).region()));

            assertThat(picked).hasSizeGreaterThan(1);
        }

        @Test
        @DisplayName("방문 통계가 아직 없어도 여러 주에 걸쳐 전국 여러 시·도에서 고른다")
        void spreadsAcrossCountry() {
            List<Region> 전국희귀 = IntStream.range(0, 40).mapToObj(index ->
                지역(String.format("KR-%02d%03d", 21 + index % 19, 100 + index), Rarity.RARE)).toList();
            Set<String> provinces = new HashSet<>();
            IntStream.range(0, 52).forEach(week -> provinces.add(MysteryDraw.draw(월요일.plusWeeks(week), 전국희귀, VisitorShares.none(),
                new MysteryRules(Set.of(Rarity.RARE), 0.3), 비밀값, Instant.EPOCH).region().value().substring(0, 5)));

            assertThat(provinces).hasSizeGreaterThanOrEqualTo(8).anyMatch(province -> province.compareTo("KR-35") > 0);
        }

        @Test
        @DisplayName("서버 비밀값이 다르면 같은 규칙과 통계로도 고르는 지역이 달라진다")
        void secretChangesPick() {
            MysterySeed 다른비밀값 = new MysterySeed("another-salt");

            assertThat(IntStream.range(0, 30).filter(week -> !MysteryDraw.draw(월요일.plusWeeks(week), 전부, 통계, 희귀전설_하위절반, 비밀값,
                Instant.EPOCH).region().equals(MysteryDraw.draw(월요일.plusWeeks(week), 전부, 통계, 희귀전설_하위절반, 다른비밀값,
                Instant.EPOCH).region())).count()).isPositive();
        }

        @Test
        @DisplayName("후보가 하나도 없으면 고를 수 없다")
        void noCandidates() {
            assertThatThrownBy(() -> MysteryDraw.draw(월요일, List.of(종로), 통계, 희귀전설_하위절반, 비밀값, Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
