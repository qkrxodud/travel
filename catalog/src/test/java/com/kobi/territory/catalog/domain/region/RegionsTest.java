package com.kobi.territory.catalog.domain.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 지역·시·도 참조 데이터의 규칙(행정구역 개편 대비: 폐지 지역은 남기되 현행에서 뺀다). */
@DisplayName("행정구역")
class RegionsTest {

    private static Region 지역(String code, String province) {
        return new Region(RegionCode.of(code), "r" + code, province, Rarity.COMMON, "KR", 1, null, null);
    }

    private static Region 폐지된지역(String code, String province, LocalDate retiredAt) {
        return new Region(RegionCode.of(code), "r" + code, province, Rarity.COMMON, "KR", 1, null, retiredAt);
    }

    @Nested
    @DisplayName("지역 목록은")
    class RegionList {

        private final Regions 서울두곳과폐지된경북 = Regions.of(List.of(지역("KR-11010", "KR-11"), 지역("KR-11020", "KR-11"),
            폐지된지역("KR-37310", "KR-37", LocalDate.of(2023, 7, 1))));

        @Test
        @DisplayName("현행 지역만 현행으로 보여 준다")
        void activeOnly() {
            assertThat(서울두곳과폐지된경북.active()).extracting(region -> region.code().value())
                .containsExactly("KR-11010", "KR-11020");
        }

        @Test
        @DisplayName("시·도별 현행 지역 수를 센다")
        void countByProvince() {
            assertThat(서울두곳과폐지된경북.activeCountByProvince()).containsExactly(Map.entry("KR-11", 2));
        }

        @Test
        @DisplayName("폐지된 지역도 코드로 찾을 수 있지만 현행은 아니다")
        void retiredStillFound() {
            assertThat(서울두곳과폐지된경북.find(RegionCode.of("KR-37310"))).get().extracting(Region::active).isEqualTo(false);
        }

        @Test
        @DisplayName("같은 지역 코드가 두 번 있으면 받지 않는다")
        void duplicateCode() {
            assertThatThrownBy(() -> Regions.of(List.of(지역("KR-11010", "KR-11"), 지역("KR-11010", "KR-11"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("중복");
        }
    }

    @Nested
    @DisplayName("시·도 목록은")
    class ProvinceList {

        private final Provinces 서울경기 = Provinces.of(List.of(new Province("KR-31", "경기", "경기도", 2, 1),
            new Province("KR-11", "서울", "서울특별시", 1, 1)));

        @Test
        @DisplayName("표시 순서대로 늘어선다")
        void displayOrder() {
            assertThat(서울경기.inDisplayOrder()).extracting(Province::name).containsExactly("서울", "경기");
        }

        @Test
        @DisplayName("시·도마다 적힌 지역 수가 실제 지역 수와 맞으면 받아들인다")
        void consistent() {
            서울경기.requireConsistentWith(Regions.of(List.of(지역("KR-11010", "KR-11"), 지역("KR-31011", "KR-31"))));
        }

        @Test
        @DisplayName("적힌 지역 수가 실제와 다르면 받지 않는다")
        void countMismatch() {
            assertThatThrownBy(() -> 서울경기.requireConsistentWith(Regions.of(List.of(지역("KR-11010", "KR-11")))))
                .hasMessageContaining("지역 수 불일치");
        }

        @Test
        @DisplayName("모르는 시·도에 속한 지역이 있으면 받지 않는다")
        void unknownProvince() {
            assertThatThrownBy(() -> 서울경기.requireConsistentWith(Regions.of(List.of(지역("KR-39010", "KR-39")))))
                .hasMessageContaining("모르는 시·도");
        }
    }
}
