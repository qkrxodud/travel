package com.kobi.territory.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.catalog.InMemoryItemDefinitionRepository;
import com.kobi.territory.catalog.domain.catalog.Catalog;
import com.kobi.territory.catalog.domain.definition.LevelRules;
import com.kobi.territory.catalog.domain.definition.LevelTitle;
import com.kobi.territory.catalog.domain.definition.ProgressionDefinitions;
import com.kobi.territory.catalog.domain.definition.ThemeDefinition;
import com.kobi.territory.catalog.domain.item.GrantRule;
import com.kobi.territory.catalog.domain.item.ItemDefinition;
import com.kobi.territory.catalog.domain.item.ItemSlot;
import com.kobi.territory.catalog.domain.region.Province;
import com.kobi.territory.catalog.domain.region.Provinces;
import com.kobi.territory.catalog.domain.region.Region;
import com.kobi.territory.catalog.domain.region.Regions;
import com.kobi.territory.catalog.domain.reward.RewardRules;
import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 운영이 아이템 정의를 더하는 흐름(서울 1곳·테마 "han"뿐인 작은 카탈로그, 종로구 특산물이 이미 있다). */
@DisplayName("운영 아이템 추가")
class ItemCatalogServiceTest {

    private static final ZoneId 서울시각 = ZoneId.of("Asia/Seoul");
    private static final RegionCode 종로구 = RegionCode.of("KR-11010");
    private static final Instant 지금 = LocalDate.of(2026, 10, 3).atTime(12, 0).atZone(서울시각).toInstant();

    private final ItemCatalogService 운영 = 운영창구();

    private static ItemCatalogService 운영창구() {
        Regions regions = Regions.of(List.of(new Region(종로구, "종로구", "KR-11", Rarity.COMMON, "KR", 1, null, null)));
        Provinces provinces = Provinces.of(List.of(new Province("KR-11", "서울", "서울특별시", 1, 1)));
        ProgressionDefinitions progression = new ProgressionDefinitions(new LevelRules(5, List.of(new LevelTitle(1, "초보"))),
            List.of(new ThemeDefinition("han", "한강", "", "t", List.of(종로구), null)), List.of(), List.of());
        RewardRules rules = new RewardRules(Map.of(Rarity.COMMON, 10, Rarity.RARE, 20, Rarity.LEGEND, 50), 15, 100, 10);
        Catalog catalog = new Catalog(regions, provinces, rules, "{}", progression);
        ItemDefinition lantern = new ItemDefinition("region:KR-11010", "이름", "*", ItemSlot.HAND, Rarity.RARE, null, null,
            new GrantRule.RegionVisit(종로구), null, 지금.minusSeconds(86_400));
        InMemoryItemDefinitionRepository repository = new InMemoryItemDefinitionRepository(List.of(lantern));
        return new ItemCatalogService(repository, new ItemDefinitionCache(repository), () -> catalog, Clock.fixed(지금, 서울시각));
    }

    private static RegisterItemCommand 정의(String itemId, ItemSlot slot, GrantRule.Type rule, String ref) {
        return new RegisterItemCommand(itemId, "이름", "*", slot, Rarity.RARE, null, null, null, null, rule, ref, null, null);
    }

    private static RegisterItemCommand 서울핀() {
        return new RegisterItemCommand("event:seoul-pin", "서울 핀", "📍", ItemSlot.BADGE, Rarity.RARE,
            null, "star", "#f4c542", "#2b3542", GrantRule.Type.PROVINCE_CHECK_IN, "KR-11", null, null);
    }

    @Test
    @DisplayName("입력한 지급 규칙과 생김새 그대로 등록된다")
    void registered() {
        var created = 운영.register(서울핀());

        assertThat(created.grantRule()).isEqualTo("PROVINCE_CHECK_IN");
        assertThat(created.look().primary()).isEqualTo("#f4c542");
    }

    @Test
    @DisplayName("등록한 뒤의 체크인부터 새 아이템을 받는다")
    void appliesToLaterCheckIns() {
        운영.register(서울핀());

        assertThat(운영.grantedByCheckIn("KR-11010", "KR-11", 지금)).extracting(view -> view.itemId())
            .containsExactly("region:KR-11010", "event:seoul-pin");
    }

    @Test
    @DisplayName("같은 아이템은 두 번 등록할 수 없다")
    void duplicate() {
        운영.register(서울핀());

        assertThatThrownBy(() -> 운영.register(정의("event:seoul-pin", ItemSlot.BADGE, GrantRule.Type.MANUAL, null)))
            .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "ITEM_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("카탈로그에 없는 시·도를 가리킬 수 없다")
    void unknownProvince() {
        assertThatThrownBy(() -> 운영.register(정의("event:busan", ItemSlot.BADGE, GrantRule.Type.PROVINCE_CHECK_IN, "KR-21")))
            .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "UNKNOWN_ITEM_REFERENCE");
    }

    @Test
    @DisplayName("카탈로그에 없는 테마를 가리킬 수 없다")
    void unknownTheme() {
        assertThatThrownBy(() -> 운영.register(정의("event:jiri", ItemSlot.BG, GrantRule.Type.THEME_COMPLETE, "jiri")))
            .hasFieldOrPropertyWithValue("code", "UNKNOWN_ITEM_REFERENCE");
    }

    @Test
    @DisplayName("테마 보상 자리의 아이템은 수동 지급으로 만들 수 없다")
    void themeSlotReserved() {
        assertThatThrownBy(() -> 운영.register(정의("set:fake", ItemSlot.BG, GrantRule.Type.MANUAL, null)))
            .hasFieldOrPropertyWithValue("code", "INVALID_ITEM_DEFINITION");
    }
}
