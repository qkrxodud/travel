package com.kobi.territory.social.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 5단계 리더 결정 5(모집단 = 활성 지역 1곳 이상인 활성 탐험가) · QA P2-4 · r2 P3-B(방문자가 모집단을 넘는 지역). */
@DisplayName("하루 한 번 세는 순위 통계")
class RankSnapshotTest {

    static final ExplorerId A = ExplorerId.of("00000000-0000-0000-0000-00000000000a");
    static final ExplorerId B = ExplorerId.of("00000000-0000-0000-0000-00000000000b");
    static final ExplorerId C = ExplorerId.of("00000000-0000-0000-0000-00000000000c");
    static final ExplorerId ZERO = ExplorerId.of("00000000-0000-0000-0000-00000000000d");
    static final ExplorerId MERGED = ExplorerId.of("00000000-0000-0000-0000-00000000000e");
    static final Instant AT = Instant.parse("2026-10-03T19:30:00Z");

    @Nested
    @DisplayName("상위 몇 %인지는")
    class TopPercent {

        @Test
        @DisplayName("순위를 모집단으로 나눈 비율을 올림한다")
        void roundsUp() {
            assertThat(RankPercentile.topPercent(2, 3)).isEqualTo(67);
            assertThat(RankPercentile.topPercent(10, 100)).isEqualTo(10);
            assertThat(RankPercentile.topPercent(11, 100)).isEqualTo(11);
        }

        @Test
        @DisplayName("혼자거나 꼴찌면 상위 100%다")
        void lastIsHundred() {
            assertThat(RankPercentile.topPercent(1, 1)).isEqualTo(100);
            assertThat(RankPercentile.topPercent(3, 3)).isEqualTo(100);
        }

        @Test
        @DisplayName("아무리 앞서도 상위 1%보다 작아지지 않는다")
        void atLeastOne() {
            assertThat(RankPercentile.topPercent(1, 100)).isEqualTo(1);
            assertThat(RankPercentile.topPercent(1, 1000)).isEqualTo(1);
        }

        @Test
        @DisplayName("모집단 밖의 순위는 셀 수 없다")
        void rejectsOutOfRange() {
            assertThatThrownBy(() -> RankPercentile.topPercent(0, 3)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RankPercentile.topPercent(4, 3)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("탐험가들의 영토로 세면")
    class Snapshot {

        /** A 10곳(서울 7·부산 3), B 5곳(부산 4·서울 1), C 5곳(서울), ZERO 0곳, MERGED 는 합쳐져 활성 탐험가가 아니다. */
        static final RankSnapshot SNAPSHOT = RankSnapshot.compute(List.of(A, B, C, ZERO), ProvinceTallies.of(List.of(
                new ProvinceTally(A, "KR-11", 7), new ProvinceTally(A, "KR-26", 3),
                new ProvinceTally(B, "KR-26", 4), new ProvinceTally(B, "KR-11", 1),
                new ProvinceTally(C, "KR-11", 5),
                new ProvinceTally(MERGED, "KR-11", 40))),
            Map.of("KR-11010", 3, "KR-26010", 1), AT);

        @Test
        @DisplayName("모집단은 한 곳 이상 칠한 활성 탐험가다")
        void population() {
            assertThat(SNAPSHOT.population()).isEqualTo(3);
        }

        @Test
        @DisplayName("한 곳도 칠하지 않은 사람은 순위가 없다")
        void zeroHasNoRank() {
            assertThat(SNAPSHOT.percentileOf(ZERO)).isEmpty();
        }

        @Test
        @DisplayName("합쳐져 활성이 아닌 탐험가는 순위가 없다")
        void mergedHasNoRank() {
            assertThat(SNAPSHOT.percentileOf(MERGED)).isEmpty();
        }

        @Test
        @DisplayName("지역 수로 순위를 매기고 같으면 같은 순위·같은 %다")
        void percentiles() {
            assertThat(SNAPSHOT.percentiles()).containsExactly(
                new RankPercentile(A, 10, 1, 3, 34, AT),
                new RankPercentile(B, 5, 2, 3, 67, AT),
                new RankPercentile(C, 5, 2, 3, 67, AT));
        }

        @Test
        @DisplayName("지역마다 다녀간 탐험가 비율을 낸다")
        void regionStats() {
            assertThat(SNAPSHOT.regionStats()).containsExactly(new RegionStat("KR-11010", 3, 3, AT), new RegionStat("KR-26010", 1, 3, AT));
            assertThat(SNAPSHOT.regionStats().get(0).visitorPercent()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("주 활동 시·도별과 전국의 평균 지역 수를 낸다")
        void provinceStats() {
            assertThat(SNAPSHOT.provinceStats()).containsExactly(
                new ProvinceStat("KR-11", 2, 15, AT), new ProvinceStat("KR-26", 1, 5, AT),
                new ProvinceStat(ProvinceStat.NATIONWIDE, 3, 20, AT));
            assertThat(SNAPSHOT.provinceStats().get(0).averageRegionCount()).isEqualTo(7.5);
            assertThat(SNAPSHOT.provinceStats().get(2).averageRegionCount()).isEqualTo(6.7);
        }
    }

    @Nested
    @DisplayName("지역 방문자가 모집단보다 많게 세어지면")
    class Overflow {

        static final RankSnapshot SNAPSHOT = RankSnapshot.compute(List.of(A),
            ProvinceTallies.of(List.of(new ProvinceTally(A, "KR-11", 1))), Map.of("KR-11010", 1, "KR-11020", 3), AT);

        @Test
        @DisplayName("그 지역만 모집단에 맞춘다")
        void capsThatRegion() {
            assertThat(SNAPSHOT.regionStats()).containsExactly(new RegionStat("KR-11010", 1, 1, AT), new RegionStat("KR-11020", 1, 1, AT));
        }

        @Test
        @DisplayName("어긋난 지역을 드러낸다")
        void reportsOverflow() {
            assertThat(SNAPSHOT.overflows()).containsExactly(new RegionOverflow("KR-11020", 3, 1));
        }

        @Test
        @DisplayName("상위 %는 그대로 낸다")
        void percentilesStillComputed() {
            assertThat(SNAPSHOT.percentiles()).hasSize(1);
        }

        @Test
        @DisplayName("한 지역 통계에 모집단보다 많은 방문자를 적을 수는 없다")
        void regionStatRejectsOverflow() {
            assertThatThrownBy(() -> new RegionStat("KR-11010", 3, 2, AT)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("아무도 칠하지 않았으면")
    class Empty {

        static final RankSnapshot SNAPSHOT = RankSnapshot.compute(List.of(), ProvinceTallies.of(List.of()), Map.of(), AT);

        @Test
        @DisplayName("순위가 하나도 없다")
        void noPercentiles() {
            assertThat(SNAPSHOT.percentiles()).isEmpty();
        }

        @Test
        @DisplayName("전국 평균만 0으로 남는다")
        void nationwideZero() {
            assertThat(SNAPSHOT.provinceStats()).containsExactly(new ProvinceStat(ProvinceStat.NATIONWIDE, 0, 0, AT));
        }

        @Test
        @DisplayName("모집단이 없는 지역의 방문자 비율은 0%다")
        void zeroPercent() {
            assertThat(new RegionStat("KR-11010", 0, 0, AT).visitorPercent()).isZero();
        }
    }
}
