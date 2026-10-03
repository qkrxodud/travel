package com.kobi.territory.social.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.domain.stats.ProvinceStat;
import com.kobi.territory.social.domain.stats.ProvinceStats;
import com.kobi.territory.social.domain.stats.ProvinceTallies;
import com.kobi.territory.social.domain.stats.ProvinceTally;
import com.kobi.territory.social.domain.stats.RankPercentile;
import com.kobi.territory.social.domain.stats.RankSnapshot;
import com.kobi.territory.social.domain.stats.RegionOverflow;
import com.kobi.territory.social.domain.stats.RegionStat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** D1: 상위 % 경계(올림·최소 1·동점·0곳 제외·비활성 제외), 주 활동 시·도, 지역 비율, 콜드 스타트 대상. */
class StatsTest {

    static final ExplorerId A = ExplorerId.of("00000000-0000-0000-0000-00000000000a");
    static final ExplorerId B = ExplorerId.of("00000000-0000-0000-0000-00000000000b");
    static final ExplorerId C = ExplorerId.of("00000000-0000-0000-0000-00000000000c");
    static final ExplorerId ZERO = ExplorerId.of("00000000-0000-0000-0000-00000000000d");
    static final ExplorerId MERGED = ExplorerId.of("00000000-0000-0000-0000-00000000000e");
    static final Instant AT = Instant.parse("2026-10-03T19:30:00Z");

    @Test
    void 상위_퍼센트는_순위_비율의_올림이고_최소_1이다() {
        assertThat(RankPercentile.topPercent(1, 1)).isEqualTo(100);
        assertThat(RankPercentile.topPercent(1, 100)).isEqualTo(1);
        assertThat(RankPercentile.topPercent(1, 1000)).isEqualTo(1);
        assertThat(RankPercentile.topPercent(2, 3)).isEqualTo(67);
        assertThat(RankPercentile.topPercent(3, 3)).isEqualTo(100);
        assertThat(RankPercentile.topPercent(10, 100)).isEqualTo(10);
        assertThat(RankPercentile.topPercent(11, 100)).isEqualTo(11);
        assertThatThrownBy(() -> RankPercentile.topPercent(0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RankPercentile.topPercent(4, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 모집단은_지역이_1곳_이상인_활성_탐험가다() {
        ProvinceTallies tallies = ProvinceTallies.of(List.of(
            new ProvinceTally(A, "KR-11", 7), new ProvinceTally(A, "KR-26", 3),   // A 10곳, 주 활동 서울
            new ProvinceTally(B, "KR-26", 4), new ProvinceTally(B, "KR-11", 1),   // B 5곳, 주 활동 부산
            new ProvinceTally(C, "KR-11", 5),                                      // C 5곳, 서울
            new ProvinceTally(MERGED, "KR-11", 40)));                              // 병합돼 비활성 — 무시

        RankSnapshot snapshot = RankSnapshot.compute(List.of(A, B, C, ZERO), tallies, Map.of("KR-11010", 3, "KR-26010", 1), AT);

        assertThat(snapshot.population()).as("지역 0곳·병합 비활성 제외(리더 결정 5)").isEqualTo(3);
        assertThat(snapshot.percentiles()).containsExactly(
            new RankPercentile(A, 10, 1, 3, 34, AT),
            new RankPercentile(B, 5, 2, 3, 67, AT),
            new RankPercentile(C, 5, 2, 3, 67, AT));
        assertThat(snapshot.percentileOf(ZERO)).isEmpty();
        assertThat(snapshot.percentileOf(MERGED)).isEmpty();
        assertThat(snapshot.regionStats()).containsExactly(new RegionStat("KR-11010", 3, 3, AT), new RegionStat("KR-26010", 1, 3, AT));
        assertThat(snapshot.regionStats().get(0).visitorPercent()).isEqualTo(100.0);
        assertThat(snapshot.provinceStats()).containsExactly(
            new ProvinceStat("KR-11", 2, 15, AT), new ProvinceStat("KR-26", 1, 5, AT), new ProvinceStat(ProvinceStat.NATIONWIDE, 3, 20, AT));
        assertThat(snapshot.provinceStats().get(0).averageRegionCount()).isEqualTo(7.5);
        assertThat(snapshot.provinceStats().get(2).averageRegionCount()).isEqualTo(6.7);
    }

    @Test
    void 방문자가_모집단을_넘은_지역은_그_지역만_맞추고_드러낸다() {
        RankSnapshot snapshot = RankSnapshot.compute(List.of(A), ProvinceTallies.of(List.of(new ProvinceTally(A, "KR-11", 1))),
            Map.of("KR-11010", 1, "KR-11020", 3), AT);
        assertThat(snapshot.regionStats()).containsExactly(new RegionStat("KR-11010", 1, 1, AT), new RegionStat("KR-11020", 1, 1, AT));
        assertThat(snapshot.overflows()).containsExactly(new RegionOverflow("KR-11020", 3, 1));
        assertThat(snapshot.percentiles()).hasSize(1); // 상위 % 는 그대로 나온다
    }

    @Test
    void 아무도_없으면_빈_스냅숏이다() {
        RankSnapshot snapshot = RankSnapshot.compute(List.of(), ProvinceTallies.of(List.of()), Map.of(), AT);
        assertThat(snapshot.percentiles()).isEmpty();
        assertThat(snapshot.provinceStats()).containsExactly(new ProvinceStat(ProvinceStat.NATIONWIDE, 0, 0, AT));
        assertThat(new RegionStat("KR-11010", 0, 0, AT).visitorPercent()).isZero();
        // 방문자가 모집단을 넘으면 원천이 어긋난 것 — 가리지 않고 실패(QA P2-4)
        assertThatThrownBy(() -> new RegionStat("KR-11010", 3, 2, AT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 주_활동_시도는_가장_많이_칠한_곳이고_같으면_코드_순이다() {
        ProvinceTallies tallies = ProvinceTallies.of(List.of(
            new ProvinceTally(A, "KR-26", 3), new ProvinceTally(A, "KR-11", 3), new ProvinceTally(B, "KR-37", 0)));
        assertThat(tallies.mainProvinceOf(A)).contains("KR-11");
        assertThat(tallies.regionCountOf(A)).isEqualTo(6);
        assertThat(tallies.mainProvinceOf(B)).isEmpty();
        assertThat(tallies.mainProvinceOf(C)).isEmpty();
    }

    @Test
    void 콜드_스타트는_친구가_없을_때만_내_시도_평균_없으면_전국_평균() {
        ProvinceStats stats = ProvinceStats.of(List.of(new ProvinceStat("KR-11", 2, 15, AT),
            new ProvinceStat(ProvinceStat.NATIONWIDE, 3, 20, AT)));

        assertThat(stats.coldStartBaseline(1, Optional.of("KR-11"))).isEmpty();
        assertThat(stats.coldStartBaseline(0, Optional.of("KR-11"))).map(ProvinceStat::provinceCode).contains("KR-11");
        assertThat(stats.coldStartBaseline(0, Optional.of("KR-50"))).map(ProvinceStat::nationwide).contains(true);
        assertThat(stats.coldStartBaseline(0, Optional.empty())).map(ProvinceStat::nationwide).contains(true);
        assertThat(ProvinceStats.of(List.of()).coldStartBaseline(0, Optional.of("KR-11"))).isEmpty();
    }
}
