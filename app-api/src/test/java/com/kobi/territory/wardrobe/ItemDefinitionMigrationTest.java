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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 아이템 정의 DB화(V3_1 이관) 데이터 검증 — 예전 catalog items.json 테스트를 DB 기준으로 옮겼다. */
@IntegrationTest
class ItemDefinitionMigrationTest {

    @Autowired RegionCatalog regions;
    @Autowired ItemCatalog items;
    @Autowired ProgressionRules progression;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 지역마다_특산물_아이템이_하나씩_있고_티어는_지역_희귀도와_같다() {
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
    void 세트마다_완성_보상_배경이_있다() {
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
    void 상호_브랜드명은_일반명사화되어_있다() {
        Set<String> names = regions.items().stream().map(ItemView::name).collect(Collectors.toSet());
        for (String brand : List.of("성심당", "이성당", "에버랜드", "라이온즈파크", "챔피언스필드", "황남빵", "예술의전당")) {
            assertThat(names).noneMatch(name -> name.contains(brand));
        }
        assertThat(regions.regionItem(RegionCode.of("KR-25040")).orElseThrow().name()).startsWith("대전 튀김소보로");
        assertThat(names).anyMatch(name -> name.startsWith("군산 단팥빵"));
    }
}
