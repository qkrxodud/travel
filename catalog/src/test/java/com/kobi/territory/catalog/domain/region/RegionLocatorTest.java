package com.kobi.territory.catalog.domain.region;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.catalog.domain.catalog.Catalog;
import com.kobi.territory.catalog.infra.repository.JsonCatalogRepository;
import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 바깥 자료의 위치(축제 좌표·주소)를 우리 시·군·구로 옮기기 — 실제 카탈로그 경계(regions.geojson)로 확인한다. 바깥 기관의 지역 코드표에 기대지
 * 않는다.
 */
@DisplayName("바깥 위치를 우리 지역으로 옮기기")
class RegionLocatorTest {

    private static final Catalog 카탈로그 = new JsonCatalogRepository().load();
    private static final RegionLocator 위치찾기 = 카탈로그.regionLocator(3);

    private static Optional<RegionMatch> 찾기(double longitude, double latitude) {
        return 위치찾기.locate(new GeoPoint(longitude, latitude), null);
    }

    private static Optional<RegionMatch> 주소로(String address) {
        return 위치찾기.locate(null, address);
    }

    @Nested
    @DisplayName("좌표가 있으면")
    class ByCoordinate {

        @Test
        @DisplayName("그 점을 품은 시·군·구가 된다")
        void inside() {
            assertThat(찾기(128.76433, 35.137)).hasValue(new RegionMatch(RegionCode.of("KR-38115"), RegionMatch.Method.INSIDE_BOUNDARY));
            assertThat(찾기(126.9092, 37.5244)).map(RegionMatch::code).hasValue(RegionCode.of("KR-11190"));
            assertThat(찾기(128.54387, 38.18247)).map(RegionMatch::code).hasValue(RegionCode.of("KR-32060"));
        }

        @Test
        @DisplayName("단순화된 경계 바로 밖의 점은 허용 거리 안의 가장 가까운 지역이 된다")
        void nearBoundary() {
            // 진해구 경계 남쪽 끝 꼭짓점에서 1km 남짓 바다 쪽 — 어느 경계 안에도 들지 않는다
            assertThat(찾기(128.761, 35.051)).hasValue(new RegionMatch(RegionCode.of("KR-38115"), RegionMatch.Method.NEAR_BOUNDARY));
        }

        @Test
        @DisplayName("주소가 다른 지역을 가리켜도 좌표를 따른다")
        void coordinateWins() {
            // 실제 TourAPI "단풍산" — 주소는 영월군 산솔면, 좌표는 정선군 안(영월 경계에서 20km)
            assertThat(위치찾기.locate(new GeoPoint(128.7310975139, 37.4133712106), "강원특별자치도 영월군 산솔면 녹전리"))
                .map(RegionMatch::code).hasValue(RegionCode.of("KR-32350"));
            assertThat(위치찾기.locate(new GeoPoint(128.76433, 35.137), "서울특별시 종로구 세종대로 1"))
                .map(RegionMatch::code).hasValue(RegionCode.of("KR-38115"));
        }

        @Test
        @DisplayName("어느 경계에서도 먼 바다 위 점은 찾지 못한다")
        void farAtSea() {
            assertThat(찾기(125.0, 34.0)).isEmpty();
        }

        @Test
        @DisplayName("좌표로 못 찾으면 주소로 찾는다")
        void fallsBackToAddress() {
            assertThat(위치찾기.locate(new GeoPoint(125.0, 34.0), "충청북도 충주시 중앙로 1"))
                .hasValue(new RegionMatch(RegionCode.of("KR-33020"), RegionMatch.Method.ADDRESS));
        }
    }

    @Nested
    @DisplayName("주소만 있으면")
    class ByAddress {

        @Test
        @DisplayName("시·도 다음 낱말과 이름이 같은 지역이 된다")
        void cityName() {
            assertThat(주소로("강원특별자치도 속초시 설악산로 1")).map(RegionMatch::code).hasValue(RegionCode.of("KR-32060"));
        }

        @Test
        @DisplayName("구가 있는 시는 시와 구를 이어 붙여 찾는다")
        void cityWithDistrict() {
            assertThat(주소로("경기도 수원시 장안구 정조로 1")).map(RegionMatch::code).hasValue(RegionCode.of("KR-31011"));
        }

        @Test
        @DisplayName("이름이 같은 지역(고성군)은 시·도로 가른다")
        void sameNameDifferentProvince() {
            assertThat(주소로("강원특별자치도 고성군 토성면")).map(RegionMatch::code).hasValue(RegionCode.of("KR-32400"));
            assertThat(주소로("경상남도 고성군 고성읍")).map(RegionMatch::code).hasValue(RegionCode.of("KR-38340"));
        }

        @Test
        @DisplayName("바뀌기 전 시·도 이름(강원도·전라북도)으로 쓴 주소도 찾는다")
        void oldProvinceNames() {
            assertThat(주소로("강원도 강릉시 경포로 1")).map(RegionMatch::code).hasValue(RegionCode.of("KR-32030"));
            assertThat(주소로("전라북도 정읍시 내장산로 1")).map(RegionMatch::code).hasValue(RegionCode.of("KR-35040"));
        }

        @Test
        @DisplayName("지역이 하나뿐인 시·도(세종)는 그 지역이 된다")
        void singleRegionProvince() {
            List<Region> 세종 = 카탈로그.regions().activeIn("KR-29");
            assertThat(주소로("세종특별자치시 연기면 1")).map(RegionMatch::code).hasValue(세종.getFirst().code());
        }

        @Test
        @DisplayName("시·도를 알 수 없거나 시·군·구를 못 찾으면 찾지 못한다")
        void unknown() {
            assertThat(주소로("어딘가 모르는 곳")).isEmpty();
            assertThat(주소로("경기도 수원시")).isEmpty();
            assertThat(위치찾기.locate(null, null)).isEmpty();
        }
    }
}
