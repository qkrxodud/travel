package com.kobi.territory.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.catalog.application.CatalogService;
import com.kobi.territory.catalog.application.ItemDefinitionCache;
import com.kobi.territory.catalog.domain.catalog.Catalog;
import com.kobi.territory.catalog.domain.region.Region;
import com.kobi.territory.catalog.infra.repository.JsonCatalogRepository;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 실제로 싣는 지역 참조 데이터(리소스 JSON, 원본: 프로토타입 → tools/catalog/gen-catalog.js). Spring 없음.
 * 아이템 정의 데이터는 DB로 옮겨 app-api 이관 테스트가 검증한다.
 */
@DisplayName("지역 참조 데이터")
class RegionDataTest {

    private static final Catalog 데이터 = new JsonCatalogRepository().load();
    private static final CatalogService 카탈로그 = new CatalogService(() -> 데이터,
        new ItemDefinitionCache(InMemoryItemDefinitionRepository.empty()));

    @Nested
    @DisplayName("전국은")
    class Country {

        @Test
        @DisplayName("현행 지역 250곳으로 이루어진다")
        void regions250() {
            assertThat(데이터.regions().all()).hasSize(250);
            assertThat(카탈로그.activeRegions()).hasSize(250);
        }

        @Test
        @DisplayName("17개 시·도가 정해진 표시 순서로 늘어선다")
        void provinces17InOrder() {
            assertThat(카탈로그.provinces()).extracting(province -> province.name())
                .containsExactly("서울", "경기", "인천", "강원", "충북", "충남", "대전", "세종", "전북", "전남", "광주",
                    "경북", "경남", "대구", "부산", "울산", "제주");
        }

        @Test
        @DisplayName("시·도마다 적힌 지역 수를 합하면 250이다")
        void provinceCountsSum() {
            assertThat(데이터.provinces().inDisplayOrder().stream().mapToInt(province -> province.regionCount()).sum())
                .isEqualTo(250);
        }
    }

    @Nested
    @DisplayName("지역은")
    class RegionShape {

        @Test
        @DisplayName("KR-00000 형식의 코드를 갖고 그 앞자리가 시·도 코드다")
        void codeFormat() {
            for (Region region : 데이터.regions().all()) {
                assertThat(region.code().value()).matches("KR-\\d{5}");
                assertThat(region.provinceCode()).isEqualTo("KR-" + region.code().value().substring(3, 5));
            }
        }

        @Test
        @DisplayName("행정구역 개편에 대비해 국가·버전·대체 코드·폐지일을 처음부터 갖는다")
        void reformReady() {
            for (Region region : 데이터.regions().all()) {
                assertThat(region.countryCode()).isEqualTo("KR");
                assertThat(region.version()).isEqualTo(1);
                assertThat(region.replacedBy()).isNull();
                assertThat(region.retiredAt()).isNull();
            }
        }

        @Test
        @DisplayName("코드로 이름과 시·도를 찾는다")
        void lookup() {
            RegionView jongno = 카탈로그.findRegion(RegionCode.of("KR-11010")).orElseThrow();

            assertThat(jongno.name()).isEqualTo("종로구");
            assertThat(jongno.provinceName()).isEqualTo("서울");
        }

        @Test
        @DisplayName("없는 코드는 찾지 못한다")
        void unknownCode() {
            assertThat(카탈로그.findRegion(RegionCode.of("KR-99999"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("희귀도는")
    class Rarities {

        @Test
        @DisplayName("지정된 10곳이 전설이다")
        void tenLegends() {
            List<RegionView> legends = 카탈로그.activeRegions().stream().filter(region -> region.rarity() == Rarity.LEGEND).toList();

            assertThat(legends).extracting(region -> region.provinceName() + " " + region.name()).containsExactlyInAnyOrder(
                "경북 울릉군", "경북 영양군", "인천 옹진군", "전남 신안군", "경북 청송군", "경북 봉화군", "강원 양구군",
                "전남 진도군", "강원 화천군", "경남 의령군");
        }

        @Test
        @DisplayName("전설이 아닌 군은 희귀, 시·구는 일반이다")
        void gunRareOthersCommon() {
            for (RegionView region : 카탈로그.activeRegions()) {
                if (region.rarity() == Rarity.LEGEND) continue;
                assertThat(region.rarity()).as(region.name()).isEqualTo(region.name().endsWith("군") ? Rarity.RARE : Rarity.COMMON);
            }
        }

        @Test
        @DisplayName("희귀 지역은 72곳이다")
        void seventyTwoRare() {
            assertThat(카탈로그.activeRegions().stream().filter(region -> region.rarity() == Rarity.RARE)).hasSize(72);
        }
    }

    @Test
    @DisplayName("체크인 보상 수치는 기획한 값이다(일반 10·희귀 20·전설 50, 시·도 첫 발 15, 테마 100, 선점 10)")
    void rewardNumbers() {
        var rules = 카탈로그.rewardRules();

        assertThat(rules.xpByRarity()).containsEntry(Rarity.COMMON, 10).containsEntry(Rarity.RARE, 20)
            .containsEntry(Rarity.LEGEND, 50);
        assertThat(rules.provinceFirstBonus()).isEqualTo(15);
        assertThat(rules.setCompleteBonus()).isEqualTo(100);
        assertThat(rules.claimBonus()).isEqualTo(10);
    }

    @Test
    @DisplayName("지도 모양은 지역 250곳의 경계를 담는다")
    void geoJson() throws Exception {
        JsonNode fc = new ObjectMapper().readTree(카탈로그.regionsGeoJson());

        assertThat(fc.get("type").asText()).isEqualTo("FeatureCollection");
        assertThat(fc.get("features")).hasSize(250);
        JsonNode first = fc.get("features").get(0);
        assertThat(first.get("properties").get("code").asText()).isEqualTo("KR-11010");
        assertThat(first.get("geometry").get("type").asText()).isIn("Polygon", "MultiPolygon");
    }
}
