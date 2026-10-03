package com.kobi.territory.catalog.domain.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.catalog.domain.definition.BadgeCondition;
import com.kobi.territory.catalog.domain.definition.BadgeDefinition;
import com.kobi.territory.catalog.domain.definition.LevelRules;
import com.kobi.territory.catalog.domain.definition.LevelTitle;
import com.kobi.territory.catalog.domain.definition.ProgressionDefinitions;
import com.kobi.territory.catalog.domain.definition.ThemeDefinition;
import com.kobi.territory.catalog.domain.item.GrantRule;
import com.kobi.territory.catalog.domain.item.ItemDefinition;
import com.kobi.territory.catalog.domain.item.ItemDefinitions;
import com.kobi.territory.catalog.domain.item.ItemSlot;
import com.kobi.territory.catalog.domain.item.ValidPeriod;
import com.kobi.territory.catalog.domain.region.Province;
import com.kobi.territory.catalog.domain.region.Provinces;
import com.kobi.territory.catalog.domain.region.Region;
import com.kobi.territory.catalog.domain.region.Regions;
import com.kobi.territory.catalog.domain.reward.RewardRules;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 카탈로그 기동 시 정합성 검사: 정의가 모르는 지역·시·도를 가리키거나 지역 특산물이 빠지면 기동하지 않는다. */
@DisplayName("카탈로그 정합성")
class CatalogTest {

    private static final RegionCode 종로구 = RegionCode.of("KR-11010");
    private static final Regions 종로구만 = Regions.of(List.of(new Region(종로구, "종로구", "KR-11", Rarity.COMMON, "KR", 1, null, null)));
    private static final Provinces 서울만 = Provinces.of(List.of(new Province("KR-11", "서울", "서울특별시", 1, 1)));
    private static final RewardRules 보상 = new RewardRules(Map.of(Rarity.COMMON, 10, Rarity.RARE, 20, Rarity.LEGEND, 50), 15, 100, 10);
    private static final LevelRules 레벨 = new LevelRules(5, List.of(new LevelTitle(1, "초보")));

    private static ItemDefinition 특산물(RegionCode code) {
        return new ItemDefinition("region:" + code.value(), "i", "*", ItemSlot.HAND, Rarity.COMMON, null, null,
            new GrantRule.RegionVisit(code), ValidPeriod.ALWAYS, Instant.EPOCH);
    }

    @Test
    @DisplayName("모든 지역에 특산물이 있으면 받아들인다")
    void itemsCoverRegions() {
        new Catalog(종로구만, 서울만, 보상, "{}").requireItemCoverage(ItemDefinitions.of(List.of(특산물(종로구))));
    }

    @Test
    @DisplayName("특산물이 빠진 지역이 있으면 기동하지 않는다")
    void missingRegionItem() {
        Catalog catalog = new Catalog(종로구만, 서울만, 보상, "{}");

        assertThatThrownBy(() -> catalog.requireItemCoverage(ItemDefinitions.of(List.of())))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("테마가 모르는 지역을 가리키면 기동하지 않는다")
    void themeWithUnknownRegion() {
        ProgressionDefinitions badTheme = new ProgressionDefinitions(레벨, List.of(new ThemeDefinition("x", "x", "", "t",
            List.of(RegionCode.of("KR-99999")), null)), List.of(), List.of());

        assertThatThrownBy(() -> new Catalog(종로구만, 서울만, 보상, "{}", badTheme)).hasMessageContaining("모르는 지역");
    }

    @Test
    @DisplayName("뱃지가 모르는 시·도를 가리키면 기동하지 않는다")
    void badgeWithUnknownProvince() {
        ProgressionDefinitions badBadge = new ProgressionDefinitions(레벨, List.of(), List.of(new BadgeDefinition("b", "1", "b", "",
            new BadgeCondition(BadgeCondition.Type.PROVINCES_COMPLETE, 0, List.of("KR-99"), null, 0))), List.of());

        assertThatThrownBy(() -> new Catalog(종로구만, 서울만, 보상, "{}", badBadge)).hasMessageContaining("모르는 시·도");
    }
}
