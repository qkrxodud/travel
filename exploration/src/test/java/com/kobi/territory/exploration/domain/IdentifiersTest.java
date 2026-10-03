package com.kobi.territory.exploration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("지역 코드와 탐험가 번호")
class IdentifiersTest {

    @Nested
    @DisplayName("지역 코드")
    class Region {

        @Test
        @DisplayName("국가 코드와 다섯 자리 숫자로 쓰고 국가를 알 수 있다")
        void format() {
            assertThat(RegionCode.of("KR-11010").countryCode()).isEqualTo("KR");
        }

        @ParameterizedTest(name = "\"{0}\"은 지역 코드가 아니다")
        @ValueSource(strings = {"11010", "KR11010", "kr-11010", "KR-1101", "KR-110100", ""})
        @DisplayName("형식이 다르면 잘못된 지역 코드로 거절된다")
        void badFormat(String bad) {
            assertThatThrownBy(() -> RegionCode.of(bad)).isInstanceOf(TerritoryException.class)
                .satisfies(exception -> assertThat(((TerritoryException) exception).code()).isEqualTo("INVALID_REGION_CODE"));
        }
    }

    @Nested
    @DisplayName("탐험가 번호")
    class Explorer {

        @Test
        @DisplayName("새 번호는 36자다")
        void newId() {
            assertThat(ExplorerId.newId().value()).hasSize(36);
        }

        @Test
        @DisplayName("형식이 다르면 받지 않는다")
        void badFormat() {
            assertThatThrownBy(() -> ExplorerId.of("nope")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("같은 번호는 같은 탐험가다")
        void equality() {
            assertThat(ExplorerId.of("11111111-1111-1111-1111-111111111111"))
                .isEqualTo(ExplorerId.of("11111111-1111-1111-1111-111111111111"));
        }
    }
}
