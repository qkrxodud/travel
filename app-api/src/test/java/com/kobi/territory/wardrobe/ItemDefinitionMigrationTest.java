package com.kobi.territory.wardrobe;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.catalog.api.query.ItemCatalog;
import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.support.IntegrationTest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 아이템 정의 DB화(V3_1 이관) 데이터 검증 — 예전 catalog items.json 테스트를 DB 기준으로 옮겼다. */
@IntegrationTest
@DisplayName("아이템 정의")
class ItemDefinitionMigrationTest {

    @Autowired RegionCatalog regions;
    @Autowired ItemCatalog items;
    @Autowired ProgressionRules progression;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("지역마다 특산물이 하나씩 있고 등급은 지역 희귀도와 같으며, 전설 지역 특산물은 배경이다")
    void oneSpecialtyPerRegion() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM item_definition WHERE grant_rule = 'REGION_VISIT' AND item_id LIKE 'region:%'",
            Integer.class)).isEqualTo(250);
        for (RegionView region : regions.activeRegions()) {
            ItemView item = regions.regionItem(RegionCode.of(region.code())).orElseThrow();
            assertThat(item.itemId()).isEqualTo("region:" + region.code());
            assertThat(item.regionCode()).isEqualTo(region.code());
            assertThat(item.grantRule()).isEqualTo("REGION_VISIT");
            assertThat(item.grantRef()).isEqualTo(region.code());
            assertThat(item.tier()).isEqualTo(region.rarity());
            assertThat(item.name()).isNotBlank();
        }
        // 전설 지역 아이템은 배경(전설 풍경)
        assertThat(regions.items().stream().filter(item -> item.itemId().startsWith("region:") && item.tier() == Rarity.LEGEND))
            .hasSize(10).allSatisfy(item -> assertThat(item.slot()).isEqualTo("BG"));
    }

    @Test
    @DisplayName("테마마다 완성 보상으로 전설 배경이 있다")
    void backgroundPerTheme() {
        for (ProgressionRules.SetView set : progression.sets()) {
            List<ItemView> rewards = items.grantedByThemeCompletion(set.id(), java.time.Instant.parse("2026-10-03T03:00:00Z"));
            assertThat(rewards).extracting(ItemView::itemId).contains("set:" + set.id());
            ItemView background = items.item("set:" + set.id()).orElseThrow();
            assertThat(background.slot()).isEqualTo("BG");
            assertThat(background.tier()).isEqualTo(Rarity.LEGEND);
            assertThat(background.name()).isEqualTo(set.backgroundName());
            assertThat(background.theme()).isNotBlank();
        }
    }

    @Test
    @DisplayName("아이템 이름에 상호·상표 대신 일반 명사를 쓴다")
    void noBrandNames() {
        Set<String> names = regions.items().stream().map(ItemView::name).collect(Collectors.toSet());
        for (String brand : List.of("성심당", "이성당", "에버랜드", "라이온즈파크", "챔피언스필드", "황남빵", "예술의전당")) {
            assertThat(names).noneMatch(name -> name.contains(brand));
        }
        assertThat(regions.regionItem(RegionCode.of("KR-25040")).orElseThrow().name()).startsWith("대전 튀김소보로");
        assertThat(names).anyMatch(name -> name.startsWith("군산 단팥빵"));
    }
}
