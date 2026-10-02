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
import org.junit.jupiter.api.Test;

/**
 * 카탈로그 리소스 JSON 정합성(Spring 없이). 데이터 원본: 프로토타입 → tools/catalog/gen-catalog.js
 * 아이템 정의는 3단계부터 DB(V3_1 이관) — 아이템 데이터 검증은 app-api ItemDefinitionMigrationTest 로 옮겼다.
 */
class CatalogDataTest {

    private static final Catalog DATA = new JsonCatalogRepository().load();
    private static final CatalogService CATALOG = new CatalogService(() -> DATA, new ItemDefinitionCache(InMemoryItemDefinitionRepository.empty()));

    @Test
    void 지역_250개_시도_17개() {
        assertThat(DATA.regions().all()).hasSize(250);
        assertThat(DATA.provinces().inDisplayOrder()).hasSize(17);
        assertThat(DATA.provinces().inDisplayOrder().stream().mapToInt(province -> province.regionCount()).sum()).isEqualTo(250);
        assertThat(CATALOG.activeRegions()).hasSize(250);
        assertThat(CATALOG.provinces()).extracting(province -> province.name())
            .containsExactly("서울", "경기", "인천", "강원", "충북", "충남", "대전", "세종", "전북", "전남", "광주",
                "경북", "경남", "대구", "부산", "울산", "제주");
    }

    @Test
    void 지역은_KR_코드_국가_버전_대체코드_폐지일을_가진다() {
        for (Region region : DATA.regions().all()) {
            assertThat(region.code().value()).matches("KR-\\d{5}");
            assertThat(region.countryCode()).isEqualTo("KR");
            assertThat(region.version()).isEqualTo(1);
            assertThat(region.replacedBy()).isNull();
            assertThat(region.retiredAt()).isNull();
            assertThat(region.provinceCode()).isEqualTo("KR-" + region.code().value().substring(3, 5));
        }
    }

    @Test
    void 희귀도는_전설_지정10곳_군은_희귀_시구는_일반() {
        List<RegionView> legends = CATALOG.activeRegions().stream().filter(region -> region.rarity() == Rarity.LEGEND).toList();
        assertThat(legends).extracting(region -> region.provinceName() + " " + region.name()).containsExactlyInAnyOrder(
            "경북 울릉군", "경북 영양군", "인천 옹진군", "전남 신안군", "경북 청송군", "경북 봉화군", "강원 양구군",
            "전남 진도군", "강원 화천군", "경남 의령군");
        for (RegionView region : CATALOG.activeRegions()) {
            if (region.rarity() == Rarity.LEGEND) continue;
            assertThat(region.rarity()).as(region.name()).isEqualTo(region.name().endsWith("군") ? Rarity.RARE : Rarity.COMMON);
        }
        assertThat(CATALOG.activeRegions().stream().filter(region -> region.rarity() == Rarity.RARE)).hasSize(72);
    }

    @Test
    void 보상_규칙은_프로토타입_상수() {
        var rules = CATALOG.rewardRules();
        assertThat(rules.xpByRarity()).containsEntry(Rarity.COMMON, 10).containsEntry(Rarity.RARE, 20)
            .containsEntry(Rarity.LEGEND, 50);
        assertThat(rules.provinceFirstBonus()).isEqualTo(15);
        assertThat(rules.setCompleteBonus()).isEqualTo(100);
        assertThat(rules.claimBonus()).isEqualTo(10);
    }

    @Test
    void GeoJSON은_지역_250개_피처() throws Exception {
        JsonNode fc = new ObjectMapper().readTree(CATALOG.regionsGeoJson());
        assertThat(fc.get("type").asText()).isEqualTo("FeatureCollection");
        assertThat(fc.get("features")).hasSize(250);
        JsonNode first = fc.get("features").get(0);
        assertThat(first.get("properties").get("code").asText()).isEqualTo("KR-11010");
        assertThat(first.get("geometry").get("type").asText()).isIn("Polygon", "MultiPolygon");
    }

    @Test
    void 지역_조회() {
        RegionView jongno = CATALOG.findRegion(RegionCode.of("KR-11010")).orElseThrow();
        assertThat(jongno.name()).isEqualTo("종로구");
        assertThat(jongno.provinceName()).isEqualTo("서울");
        assertThat(CATALOG.findRegion(RegionCode.of("KR-99999"))).isEmpty();
    }
}
