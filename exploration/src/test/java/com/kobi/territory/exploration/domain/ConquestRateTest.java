package com.kobi.territory.exploration.domain;

import static com.kobi.territory.exploration.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.exploration.domain.Fixtures.JONGNO;
import static com.kobi.territory.exploration.domain.Fixtures.JUNG;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.exploration.domain.ConquestRate.ProvinceRate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConquestRateTest {

    @Test
    void 전국과_시도별_정복률을_표시_순서대로_계산한다() {
        Map<String, Integer> totals = new LinkedHashMap<>();
        totals.put("KR-11", 2);
        totals.put("KR-31", 42);
        totals.put("KR-39", 2);
        ConquestRate r = ConquestRate.of(List.of(JONGNO, JUNG, GAPYEONG), totals);
        assertThat(r.visited()).isEqualTo(3);
        assertThat(r.total()).isEqualTo(46);
        assertThat(r.percent()).isEqualTo(7);
        assertThat(r.provinces()).containsExactly(
            new ProvinceRate("KR-11", 2, 2), new ProvinceRate("KR-31", 1, 42), new ProvinceRate("KR-39", 0, 2));
        assertThat(r.provinces().get(0).conquered()).isTrue();
        assertThat(r.provinces().get(0).percent()).isEqualTo(100);
        assertThat(r.provinces().get(1).percent()).isEqualTo(2);
    }

    @Test
    void 빈_영토는_0퍼센트() {
        ConquestRate r = ConquestRate.of(List.of(), Map.of("KR-11", 25));
        assertThat(r.percent()).isZero();
        assertThat(r.provinces().get(0).conquered()).isFalse();
    }
}
