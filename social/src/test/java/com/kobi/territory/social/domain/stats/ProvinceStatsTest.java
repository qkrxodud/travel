package com.kobi.territory.social.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: §7 콜드 스타트(친구 0명이면 내 지역 평균 유저와 비교). */
@DisplayName("시·도 평균 비교")
class ProvinceStatsTest {

    static final ExplorerId A = ExplorerId.of("00000000-0000-0000-0000-00000000000a");
    static final ExplorerId B = ExplorerId.of("00000000-0000-0000-0000-00000000000b");
    static final ExplorerId C = ExplorerId.of("00000000-0000-0000-0000-00000000000c");
    static final Instant AT = Instant.parse("2026-10-03T19:30:00Z");

    @Nested
    @DisplayName("주 활동 시·도는")
    class MainProvince {

        /** A 는 부산 3·서울 3, B 는 경북 0곳. */
        static final ProvinceTallies TALLIES = ProvinceTallies.of(List.of(
            new ProvinceTally(A, "KR-26", 3), new ProvinceTally(A, "KR-11", 3), new ProvinceTally(B, "KR-37", 0)));

        @Test
        @DisplayName("가장 많이 칠한 곳이고 같으면 시·도 코드가 앞선 쪽이다")
        void mostPaintedThenCode() {
            assertThat(TALLIES.mainProvinceOf(A)).contains("KR-11");
        }

        @Test
        @DisplayName("모든 시·도의 지역 수를 더해 영토 수를 낸다")
        void regionCount() {
            assertThat(TALLIES.regionCountOf(A)).isEqualTo(6);
        }

        @Test
        @DisplayName("칠한 곳이 없거나 기록이 없는 사람은 주 활동 시·도가 없다")
        void none() {
            assertThat(TALLIES.mainProvinceOf(B)).isEmpty();
            assertThat(TALLIES.mainProvinceOf(C)).isEmpty();
        }
    }

    @Nested
    @DisplayName("친구가 없을 때 비교 대상은")
    class ColdStart {

        static final ProvinceStats STATS = ProvinceStats.of(List.of(new ProvinceStat("KR-11", 2, 15, AT),
            new ProvinceStat(ProvinceStat.NATIONWIDE, 3, 20, AT)));

        @Test
        @DisplayName("내 주 활동 시·도의 평균 탐험가다")
        void myProvince() {
            assertThat(STATS.coldStartBaseline(0, Optional.of("KR-11"))).map(ProvinceStat::provinceCode).contains("KR-11");
        }

        @Test
        @DisplayName("내 시·도 통계가 없으면 전국 평균이다")
        void nationwideWhenProvinceMissing() {
            assertThat(STATS.coldStartBaseline(0, Optional.of("KR-50"))).map(ProvinceStat::nationwide).contains(true);
        }

        @Test
        @DisplayName("주 활동 시·도가 없으면 전국 평균이다")
        void nationwideWhenNoMainProvince() {
            assertThat(STATS.coldStartBaseline(0, Optional.empty())).map(ProvinceStat::nationwide).contains(true);
        }

        @Test
        @DisplayName("통계가 아직 없으면 비교하지 않는다")
        void noneWithoutStats() {
            assertThat(ProvinceStats.of(List.of()).coldStartBaseline(0, Optional.of("KR-11"))).isEmpty();
        }
    }

    @Test
    @DisplayName("친구가 있으면 평균과 비교하지 않는다")
    void friendsNoBaseline() {
        ProvinceStats stats = ProvinceStats.of(List.of(new ProvinceStat("KR-11", 2, 15, AT)));

        assertThat(stats.coldStartBaseline(1, Optional.of("KR-11"))).isEmpty();
    }
}
