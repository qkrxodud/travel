package com.kobi.territory.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 두 탐험가 영토의 VS 비교(공유 카드와 친구 비교가 같이 쓴다). */
@DisplayName("영토 비교")
class TerritoryComparisonTest {

    @Nested
    @DisplayName("두 영토가 일부 겹치면")
    class Overlapping {

        /** 나: 종로구·중구·부산 중구(종로구가 한 번 더 들어옴), 상대: 부산 중구·울릉군·중구. */
        private final TerritoryComparison 비교 = TerritoryComparison.of(List.of("KR-11020", "KR-11010", "KR-26010", "KR-11010"),
            List.of("KR-26010", "KR-37430", "KR-11020"));

        @Test
        @DisplayName("나만·둘 다·상대만 간 지역으로 나누고 각각 코드 순으로 늘어놓는다")
        void splitsAndSorts() {
            assertThat(비교.onlyMine()).containsExactly("KR-11010");
            assertThat(비교.both()).containsExactly("KR-11020", "KR-26010");
            assertThat(비교.onlyTheirs()).containsExactly("KR-37430");
        }

        @Test
        @DisplayName("같은 지역이 여러 번 들어와도 한 곳으로 센다")
        void duplicatesCountOnce() {
            assertThat(비교.mineCount()).isEqualTo(3);
            assertThat(비교.theirsCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("영토 수가 같으면 앞서지도 뒤지지도 않는다")
        void tie() {
            assertThat(비교.lead()).isZero();
        }
    }

    @Nested
    @DisplayName("내 영토가 비어 있으면")
    class EmptyMine {

        private final TerritoryComparison 비교 = TerritoryComparison.of(List.of(), List.of("KR-11010"));

        @Test
        @DisplayName("상대 지역은 모두 상대만 간 곳이다")
        void allTheirs() {
            assertThat(비교.onlyMineCount()).isZero();
            assertThat(비교.bothCount()).isZero();
            assertThat(비교.onlyTheirsCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("상대 영토 수만큼 뒤진다")
        void behind() {
            assertThat(비교.lead()).isEqualTo(-1);
        }
    }

    @Test
    @DisplayName("같은 영토끼리의 비교 결과는 같다")
    void equalComparisons() {
        assertThat(TerritoryComparison.of(List.of(), List.of())).isEqualTo(TerritoryComparison.of(List.of(), List.of()));
    }
}
