package com.kobi.territory.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.RewardLineView;
import com.kobi.territory.catalog.application.CatalogService;
import com.kobi.territory.catalog.domain.definition.BadgeCondition;
import com.kobi.territory.catalog.domain.definition.BadgeDefinition;
import com.kobi.territory.catalog.domain.catalog.Catalog;
import com.kobi.territory.catalog.domain.definition.ThemeDefinition;
import com.kobi.territory.catalog.domain.item.ItemDefinitions;
import com.kobi.territory.catalog.domain.item.ItemDefinition;
import com.kobi.territory.catalog.domain.item.ItemSlot;
import com.kobi.territory.catalog.domain.definition.LevelRules;
import com.kobi.territory.catalog.domain.definition.LevelTitle;
import com.kobi.territory.catalog.domain.definition.ProgressionDefinitions;
import com.kobi.territory.catalog.domain.region.Province;
import com.kobi.territory.catalog.domain.region.Provinces;
import com.kobi.territory.catalog.domain.region.Region;
import com.kobi.territory.catalog.domain.region.Regions;
import com.kobi.territory.catalog.domain.reward.RewardLine;
import com.kobi.territory.catalog.domain.reward.RewardRules;
import com.kobi.territory.catalog.domain.reward.RewardSource;
import com.kobi.territory.catalog.infra.repository.JsonCatalogRepository;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 2단계 진행 정의 데이터(프로토타입 → gen-catalog.js)와 보상 함수(D1) — Spring 없음. */
class ProgressionDataTest {

    private static final Catalog DATA = new JsonCatalogRepository().load();
    private static final CatalogService CATALOG = new CatalogService(() -> DATA);
    private static final RewardRules RULES = new RewardRules(Map.of(Rarity.COMMON, 10, Rarity.RARE, 20, Rarity.LEGEND, 50),
        15, 100, 10);

    @Test
    void 보상_함수는_기본_시도첫발_선점_줄을_사실_값으로만_만든다() {
        assertThat(RULES.checkIn(Rarity.COMMON, true, true)).containsExactly(
            new RewardLine(RewardSource.REGION_BASE, 10), new RewardLine(RewardSource.PROVINCE_FIRST, 15),
            new RewardLine(RewardSource.FIRST_CLAIM, 10));
        assertThat(RULES.checkIn(Rarity.LEGEND, false, false)).containsExactly(new RewardLine(RewardSource.REGION_BASE, 50));
        assertThat(RULES.checkIn(Rarity.RARE, false, true)).extracting(RewardLine::amount).containsExactly(20, 10);
        assertThat(RULES.setComplete()).isEqualTo(new RewardLine(RewardSource.SET_COMPLETE, 100));
        // 공개 Query 도 같은 함수(카탈로그 값)
        assertThat(CATALOG.checkIn(Rarity.RARE, true, true)).containsExactly(new RewardLineView("REGION_BASE", 20),
            new RewardLineView("PROVINCE_FIRST", 15), new RewardLineView("FIRST_CLAIM", 10));
        assertThat(CATALOG.setComplete().amount()).isEqualTo(100);
    }

    @Test
    void 레벨_곡선과_칭호는_프로토타입_LEVEL_TITLES() {
        assertThat(CATALOG.levelDivisor()).isEqualTo(5);
        assertThat(CATALOG.levelTitles()).extracting(ProgressionRules.LevelTitleView::name)
            .containsExactly("초보 탐험가", "동네 산책러", "길 위의 사람", "전국 유랑객", "팔도 정복자", "영토의 주인");
    }

    @Test
    void 도감_세트_9개는_카탈로그_지역만_가리킨다() {
        assertThat(CATALOG.sets()).hasSize(9);
        assertThat(CATALOG.sets()).extracting(ProgressionRules.SetView::id)
            .containsExactly("east", "sea", "old", "island", "ball", "soup", "dmz", "jiri", "han");
        var jiri = CATALOG.sets().stream().filter(set -> set.id().equals("jiri")).findFirst().orElseThrow();
        assertThat(jiri.title()).isEqualTo("산 사람");
        assertThat(jiri.regionCodes()).containsExactly("KR-35050", "KR-36330", "KR-38360", "KR-38370", "KR-38380");
        assertThat(CATALOG.sets().stream().mapToInt(set -> set.regionCodes().size()).sum()).isEqualTo(9 + 6 + 6 + 8 + 9 + 6 + 8 + 5 + 10);
        CATALOG.sets().forEach(set -> set.regionCodes().forEach(regionCode ->
            assertThat(CATALOG.findRegion(RegionCode.of(regionCode))).as(regionCode).isPresent()));
    }

    @Test
    void 뱃지_12개와_조건() {
        assertThat(CATALOG.badges()).hasSize(12);
        var byId = CATALOG.badges().stream().collect(java.util.stream.Collectors.toMap(ProgressionRules.BadgeView::id, badge -> badge));
        assertThat(byId.get("ten").condition()).extracting("type", "min").containsExactly("REGION_COUNT", 10);
        assertThat(byId.get("capital").condition().provinces()).containsExactly("KR-11", "KR-31", "KR-23");
        assertThat(byId.get("samnam").condition().groups()).hasSize(3);
        assertThat(byId.get("half").condition().ratio()).isEqualTo(0.5);
        assertThat(byId.get("streak3").condition().type()).isEqualTo("STREAK_MONTHS");
    }

    @Test
    void 퀘스트는_월간_4개_상시_3개() {
        assertThat(CATALOG.quests()).extracting(quest -> quest.id() + ":" + quest.scope() + ":" + quest.target() + ":" + quest.xp())
            .containsExactly("m3:MONTHLY:3:60", "mgun:MONTHLY:1:40", "mprov:MONTHLY:1:80", "mset:MONTHLY:2:50",
                "leg5:ALWAYS:5:150", "gun30:ALWAYS:30:150", "p3:ALWAYS:17:200");
        assertThat(CATALOG.quests().stream().filter(quest -> quest.id().equals("p3")).findFirst().orElseThrow().param()).isEqualTo(3);
    }

    @Test
    void 칭호는_레벨6_세트9_상시3_시도17() {
        assertThat(CATALOG.titles()).hasSize(6 + 9 + 3 + 17);
        assertThat(CATALOG.titles()).extracting(ProgressionRules.TitleView::id)
            .contains("lv1", "lv16", "set-jiri", "long-leg5", "own-KR-11");
        assertThat(CATALOG.titles().stream().filter(title -> title.id().equals("own-KR-11")).findFirst().orElseThrow().name())
            .isEqualTo("서울의 주인");
    }

    @Test
    void 정합성_세트가_모르는_지역이나_뱃지가_모르는_시도를_가리키면_기동_실패() {
        Regions regions = Regions.of(List.of(new Region(RegionCode.of("KR-11010"), "종로구", "KR-11", Rarity.COMMON, "KR", 1,
            null, null)));
        Provinces provinces = Provinces.of(List.of(new Province("KR-11", "서울", "서울특별시", 1, 1)));
        ItemDefinitions items = ItemDefinitions.of(List.of(new ItemDefinition("region:KR-11010",
            RegionCode.of("KR-11010"), "i", "*", ItemSlot.HAND, Rarity.COMMON, null, null)));
        LevelRules levels = new LevelRules(5, List.of(new LevelTitle(1, "초보")));
        var badSet = new ProgressionDefinitions(levels, List.of(new ThemeDefinition("x", "x", "", "t",
            List.of(RegionCode.of("KR-99999")), null)), List.of(), List.of());
        assertThatThrownBy(() -> new Catalog(regions, provinces, items, RULES, "{}", badSet))
            .hasMessageContaining("모르는 지역");
        var badBadge = new ProgressionDefinitions(levels, List.of(), List.of(new BadgeDefinition("b", "1", "b", "",
            new BadgeCondition(BadgeCondition.Type.PROVINCES_COMPLETE, 0, List.of("KR-99"), null, 0))), List.of());
        assertThatThrownBy(() -> new Catalog(regions, provinces, items, RULES, "{}", badBadge))
            .hasMessageContaining("모르는 시·도");
    }
}
