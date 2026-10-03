package com.kobi.territory.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** D1: VS 집합 연산(나만·둘 다·상대만) — 중복 입력·빈 영토·정렬. */
class TerritoryComparisonTest {

    @Test
    void 나만_둘다_상대만을_코드_순으로_나눈다() {
        TerritoryComparison comparison = TerritoryComparison.of(List.of("KR-11020", "KR-11010", "KR-26010", "KR-11010"),
            List.of("KR-26010", "KR-37430", "KR-11020"));

        assertThat(comparison.onlyMine()).containsExactly("KR-11010");
        assertThat(comparison.both()).containsExactly("KR-11020", "KR-26010");
        assertThat(comparison.onlyTheirs()).containsExactly("KR-37430");
        assertThat(comparison.mineCount()).isEqualTo(3);
        assertThat(comparison.theirsCount()).isEqualTo(3);
        assertThat(comparison.lead()).isZero();
    }

    @Test
    void 한쪽이_비면_모두_한쪽만이다() {
        TerritoryComparison comparison = TerritoryComparison.of(List.of(), List.of("KR-11010"));

        assertThat(comparison.onlyMineCount()).isZero();
        assertThat(comparison.bothCount()).isZero();
        assertThat(comparison.onlyTheirsCount()).isEqualTo(1);
        assertThat(comparison.lead()).isEqualTo(-1);
        assertThat(TerritoryComparison.of(List.of(), List.of())).isEqualTo(TerritoryComparison.of(List.of(), List.of()));
    }
}
