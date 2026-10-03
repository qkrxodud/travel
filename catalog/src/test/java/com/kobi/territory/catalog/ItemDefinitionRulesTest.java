package com.kobi.territory.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.catalog.application.ItemCatalogService;
import com.kobi.territory.catalog.application.ItemDefinitionCache;
import com.kobi.territory.catalog.application.RegisterItemCommand;
import com.kobi.territory.catalog.domain.catalog.Catalog;
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
import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 아이템 정의 DB화(3단계): 지급 규칙 판정·유효 기간·운영 폼 입력 검증·참조 검증·중복 — Spring 없음. */
class ItemDefinitionRulesTest {

    static final RegionCode JONGNO = RegionCode.of("KR-11010");
    static final RegionCode HAEUNDAE = RegionCode.of("KR-21090");
    static final LocalDate DAY = LocalDate.of(2026, 10, 3);
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 정의 생성 시각: 2026-10-02 정오(KST). */
    static final Instant CREATED = LocalDate.of(2026, 10, 2).atTime(12, 0).atZone(SEOUL).toInstant();
    static final Instant NOON = DAY.atTime(12, 0).atZone(SEOUL).toInstant();

    static Instant noonOf(LocalDate day) {
        return day.atTime(12, 0).atZone(SEOUL).toInstant();
    }

    static ItemDefinition item(String itemId, ItemSlot slot, GrantRule rule, ValidPeriod period) {
        return new ItemDefinition(itemId, "이름", "*", slot, Rarity.RARE, null, null, rule, period, CREATED);
    }

    static final ItemDefinition LANTERN = item("region:KR-11010", ItemSlot.HAND, new GrantRule.RegionVisit(JONGNO), null);
    static final ItemDefinition CHUSEOK = item("event:chuseok", ItemSlot.HAT, new GrantRule.PeriodCheckIn(),
        new ValidPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9)));
    static final ItemDefinition SEOUL_PIN = item("event:seoul-pin", ItemSlot.BADGE, new GrantRule.ProvinceCheckIn("KR-11"), null);
    static final ItemDefinition HAN_BG = item("set:han", ItemSlot.BG, new GrantRule.ThemeComplete("han"), null);
    static final ItemDefinition MANUAL = item("event:gift", ItemSlot.PET, new GrantRule.Manual(), null);
    static final ItemDefinitions ALL = ItemDefinitions.of(List.of(LANTERN, CHUSEOK, SEOUL_PIN, HAN_BG, MANUAL));

    @Test
    void 체크인_한_번으로_지역_아이템_기간_이슈_시도_이슈를_받는다() {
        assertThat(ALL.grantedByCheckIn(JONGNO, "KR-11", DAY, NOON)).extracting(ItemDefinition::itemId)
            .containsExactly("region:KR-11010", "event:chuseok", "event:seoul-pin");
        assertThat(ALL.grantedByCheckIn(HAEUNDAE, "KR-21", DAY, NOON)).extracting(ItemDefinition::itemId)
            .containsExactly("event:chuseok");
        // 기간 밖(양 끝 포함)
        assertThat(ALL.grantedByCheckIn(HAEUNDAE, "KR-21", LocalDate.of(2026, 10, 9), noonOf(LocalDate.of(2026, 10, 9)))).hasSize(1);
        assertThat(ALL.grantedByCheckIn(HAEUNDAE, "KR-21", LocalDate.of(2026, 10, 10), noonOf(LocalDate.of(2026, 10, 10)))).isEmpty();
    }

    @Test
    void 이슈_아이템은_정의가_생기기_전_체크인에_소급_지급하지_않는다_지역_아이템은_상관없다_Q_R2_1() {
        Instant morning = LocalDate.of(2026, 10, 2).atTime(9, 0).atZone(SEOUL).toInstant(); // 기간 안, 정의 생성 전
        assertThat(ALL.grantedByCheckIn(JONGNO, "KR-11", LocalDate.of(2026, 10, 2), morning)).extracting(ItemDefinition::itemId)
            .containsExactly("region:KR-11010");
        assertThat(ALL.grantedByCheckIn(JONGNO, "KR-11", LocalDate.of(2026, 10, 2), CREATED)).extracting(ItemDefinition::itemId)
            .containsExactly("region:KR-11010", "event:chuseok", "event:seoul-pin");
    }

    @Test
    void 테마_완성은_그_세트_보상만_수동_아이템은_자동_지급되지_않는다() {
        assertThat(ALL.grantedByThemeCompletion("han", DAY, NOON)).containsExactly(HAN_BG);
        assertThat(ALL.grantedByThemeCompletion("jiri", DAY, NOON)).isEmpty();
        assertThat(ALL.stream().filter(item -> item.grantedByCheckIn(JONGNO, "KR-11", DAY, NOON)))
            .doesNotContain(MANUAL);
    }

    @Test
    void 운영이_추가한_테마_완성_보상은_정의가_생긴_뒤의_완성에만_세트_배경은_예외_P3_R3_3() {
        ItemDefinition hanBonus = item("event:han-bonus", ItemSlot.BADGE, new GrantRule.ThemeComplete("han"), null);
        ItemDefinitions withBonus = ItemDefinitions.of(List.of(HAN_BG, hanBonus));
        Instant beforeDefinition = CREATED.minusSeconds(60);
        // 정의 생성 전 완성: 세트 배경(이관)만, 운영 보상은 소급 없음
        assertThat(withBonus.grantedByThemeCompletion("han", LocalDate.of(2026, 10, 2), beforeDefinition)).containsExactly(HAN_BG);
        // 생성 시각 이후 완성: 둘 다
        assertThat(withBonus.grantedByThemeCompletion("han", LocalDate.of(2026, 10, 2), CREATED)).containsExactly(HAN_BG, hanBonus);
    }

    @Test
    void 운영_폼_입력_검증() {
        assertThatThrownBy(() -> item("bad id", ItemSlot.HAT, new GrantRule.Manual(), null))
            .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "INVALID_ITEM_DEFINITION");
        assertThatThrownBy(() -> item("event:x", ItemSlot.HAT, new GrantRule.PeriodCheckIn(), null))
            .hasMessageContaining("유효 기간");
        assertThatThrownBy(() -> new ValidPeriod(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 1)))
            .hasMessageContaining("앞입니다");
        assertThatThrownBy(() -> new ItemDefinition.Look("lantern", "red", "#ffffff")).hasMessageContaining("#rrggbb");
        assertThatThrownBy(() -> GrantRule.of(GrantRule.Type.PROVINCE_CHECK_IN, "서울")).hasMessageContaining("시·도 코드");
        assertThatThrownBy(() -> GrantRule.of(GrantRule.Type.THEME_COMPLETE, " ")).hasMessageContaining("세트 id");
        assertThatThrownBy(() -> new ItemDefinition("event:x", "x".repeat(41), "*", ItemSlot.HAT, Rarity.RARE, null, null,
            new GrantRule.Manual(), null, CREATED)).hasMessageContaining("이름");
    }

    @Test
    void 운영_추가는_참조_대상이_있어야_하고_같은_id_는_한_번만() {
        Regions regions = Regions.of(List.of(new Region(JONGNO, "종로구", "KR-11", Rarity.COMMON, "KR", 1, null, null)));
        Provinces provinces = Provinces.of(List.of(new Province("KR-11", "서울", "서울특별시", 1, 1)));
        ProgressionDefinitions progression = new ProgressionDefinitions(new LevelRules(5, List.of(new LevelTitle(1, "초보"))),
            List.of(new ThemeDefinition("han", "한강", "", "t", List.of(JONGNO), null)), List.of(), List.of());
        RewardRules rules = new RewardRules(Map.of(Rarity.COMMON, 10, Rarity.RARE, 20, Rarity.LEGEND, 50), 15, 100, 10);
        Catalog catalog = new Catalog(regions, provinces, rules, "{}", progression);
        InMemoryItemDefinitionRepository repository = new InMemoryItemDefinitionRepository(List.of(LANTERN));
        ItemCatalogService service = new ItemCatalogService(repository, new ItemDefinitionCache(repository), () -> catalog,
            Clock.fixed(NOON, SEOUL));

        var created = service.register(new RegisterItemCommand("event:seoul-pin", "서울 핀", "📍", ItemSlot.BADGE, Rarity.RARE,
            null, "star", "#f4c542", "#2b3542", GrantRule.Type.PROVINCE_CHECK_IN, "KR-11", null, null));
        assertThat(created.grantRule()).isEqualTo("PROVINCE_CHECK_IN");
        assertThat(created.look().primary()).isEqualTo("#f4c542");
        assertThat(service.grantedByCheckIn("KR-11010", "KR-11", NOON)).extracting(view -> view.itemId())
            .containsExactly("region:KR-11010", "event:seoul-pin");

        assertThatThrownBy(() -> service.register(new RegisterItemCommand("event:seoul-pin", "중복", "📍", ItemSlot.BADGE,
            Rarity.RARE, null, null, null, null, GrantRule.Type.MANUAL, null, null, null)))
            .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "ITEM_ALREADY_EXISTS");
        assertThatThrownBy(() -> service.register(new RegisterItemCommand("event:busan", "부산", "🌊", ItemSlot.BADGE,
            Rarity.RARE, null, null, null, null, GrantRule.Type.PROVINCE_CHECK_IN, "KR-21", null, null)))
            .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "UNKNOWN_ITEM_REFERENCE");
        assertThatThrownBy(() -> service.register(new RegisterItemCommand("set:fake", "예약", "⛰️", ItemSlot.BG,
            Rarity.LEGEND, null, null, null, null, GrantRule.Type.MANUAL, null, null, null)))
            .hasFieldOrPropertyWithValue("code", "INVALID_ITEM_DEFINITION");
        assertThatThrownBy(() -> service.register(new RegisterItemCommand("event:jiri", "지리", "⛰️", ItemSlot.BG,
            Rarity.LEGEND, null, null, null, null, GrantRule.Type.THEME_COMPLETE, "jiri", null, null)))
            .hasFieldOrPropertyWithValue("code", "UNKNOWN_ITEM_REFERENCE");
    }
}
