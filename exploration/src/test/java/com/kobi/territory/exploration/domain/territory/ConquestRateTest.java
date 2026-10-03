package com.kobi.territory.exploration.domain.territory;

import static com.kobi.territory.exploration.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.exploration.domain.Fixtures.JONGNO;
import static com.kobi.territory.exploration.domain.Fixtures.JUNG;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.territory;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.exploration.domain.territory.ConquestRate.ProvinceRate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("정복률")
class ConquestRateTest {

    @Nested
    @DisplayName("서울 2곳 전부와 경기 1곳을 칠했을 때")
    class Painted {

        ConquestRate rate() {
            Map<String, Integer> totals = new LinkedHashMap<>();
            totals.put("KR-11", 2);
            totals.put("KR-31", 42);
            totals.put("KR-39", 2);
            return ConquestRate.of(List.of(JONGNO, JUNG, GAPYEONG), totals);
        }

        @Test
        @DisplayName("전국 정복률은 칠한 지역 수를 전체 지역 수로 나눈 백분율이다")
        void national() {
            ConquestRate rate = rate();
            assertThat(rate.visited()).isEqualTo(3);
            assertThat(rate.total()).isEqualTo(46);
            assertThat(rate.percent()).isEqualTo(7);
        }

        @Test
        @DisplayName("시·도별 정복률은 정해진 표시 순서대로 나온다")
        void provincesInOrder() {
            assertThat(rate().provinces()).containsExactly(
                new ProvinceRate("KR-11", 2, 2), new ProvinceRate("KR-31", 1, 42), new ProvinceRate("KR-39", 0, 2));
            assertThat(rate().provinces().get(1).percent()).isEqualTo(2);
        }

        @Test
        @DisplayName("모든 지역을 칠한 시·도는 정복한 시·도다")
        void conqueredProvince() {
            assertThat(rate().provinces().get(0).conquered()).isTrue();
            assertThat(rate().provinces().get(0).percent()).isEqualTo(100);
        }
    }

    @Test
    @DisplayName("빈 영토는 0퍼센트이고 정복한 시·도가 없다")
    void empty() {
        ConquestRate rate = ConquestRate.of(List.of(), Map.of("KR-11", 25));
        assertThat(rate.percent()).isZero();
        assertThat(rate.provinces().get(0).conquered()).isFalse();
    }

    @Test
    @DisplayName("영토의 정복률은 지도에 칠해진 지역으로 계산한다")
    void fromTerritory() {
        Territory painted = territory().paint(ME, JONGNO).build();
        var rate = painted.conquest(new LinkedHashMap<>(Map.of("KR-11", 25)));
        assertThat(rate.visited()).isEqualTo(1);
        assertThat(rate.provinces().get(0).visited()).isEqualTo(1);
    }
}
