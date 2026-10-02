package com.kobi.territory.catalog.application;

import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.RewardCalculator;
import com.kobi.territory.catalog.api.query.RewardLineView;
import com.kobi.territory.catalog.api.query.ProvinceView;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.catalog.api.query.RewardRulesView;
import com.kobi.territory.catalog.domain.catalog.Catalog;
import com.kobi.territory.catalog.domain.catalog.CatalogRepository;
import com.kobi.territory.catalog.domain.item.ItemDefinition;
import com.kobi.territory.catalog.domain.region.Province;
import com.kobi.territory.catalog.domain.definition.ProgressionDefinitions;
import com.kobi.territory.catalog.domain.region.Region;
import com.kobi.territory.catalog.domain.reward.RewardRules;
import com.kobi.territory.catalog.domain.reward.RewardLine;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * {@link RegionCatalog}·{@link RewardCalculator}·{@link ProgressionRules} 구현 — 저장소에서 Catalog를 불러와
 * 도메인에 묻고 view로 매핑만 한다. 필터·정렬·검증·보상 계산은 Regions·Provinces·ItemDefinitions·RewardRules·
 * ProgressionDefinitions(도메인)가 한다. 아이템 정의는 DB(item_definition, ItemDefinitionRepository)에서 읽는다.
 */
@Service
public class CatalogService implements RegionCatalog, RewardCalculator, ProgressionRules {

    private final Catalog catalog;
    private final List<RegionView> activeRegions;
    private final List<ProvinceView> provinces;
    private final ItemDefinitionCache itemDefinitions;
    private final RewardRulesView rewardRules;
    private final List<SetView> sets;
    private final List<BadgeView> badges;
    private final List<QuestView> quests;
    private final List<TitleView> titles;
    private final List<LevelTitleView> levelTitles;

    public CatalogService(CatalogRepository repository, ItemDefinitionCache itemDefinitions) {
        this.catalog = repository.load();
        this.itemDefinitions = itemDefinitions;
        this.activeRegions = catalog.regions().active().stream().map(this::toView).toList();
        this.provinces = catalog.provinces().inDisplayOrder().stream().map(CatalogService::toView).toList();
        RewardRules rules = catalog.rewardRules();
        this.rewardRules = new RewardRulesView(rules.xpByRarity(), rules.provinceFirstBonus(), rules.setCompleteBonus(),
            rules.claimBonus());
        ProgressionDefinitions definitions = catalog.progression();
        this.levelTitles = definitions.levels().titles().stream().map(levelTitle -> new LevelTitleView(levelTitle.level(), levelTitle.name())).toList();
        this.sets = definitions.themes().stream().map(theme -> new SetView(theme.id(), theme.name(), theme.desc(), theme.title(),
            theme.regions().stream().map(RegionCode::value).toList(),
            theme.background() == null ? null : theme.background().name())).toList();
        this.badges = definitions.badges().stream().map(badge -> new BadgeView(badge.id(), badge.ico(), badge.name(), badge.desc(),
            new BadgeConditionView(badge.condition().type().name(), badge.condition().min(), badge.condition().provinces(),
                badge.condition().groups(), badge.condition().ratio()))).toList();
        this.quests = definitions.quests().stream().map(quest -> new QuestView(quest.id(), quest.scope().name(), quest.ico(), quest.name(), quest.desc(),
            quest.metric().name(), quest.param(), quest.target(), quest.xp(), quest.title())).toList();
        this.titles = catalog.titles().stream()
            .map(title -> new TitleView(title.id(), title.name(), title.how(), title.source().name(), title.ref())).toList();
    }

    // ---- RewardCalculator (D1: 탐험·진행이 같은 순수 함수를 호출) ----

    @Override
    public List<RewardLineView> checkIn(Rarity rarity, boolean firstInProvince, boolean firstClaim) {
        return catalog.rewardRules().checkIn(rarity, firstInProvince, firstClaim).stream().map(CatalogService::toView).toList();
    }

    @Override
    public RewardLineView setComplete() {
        return toView(catalog.rewardRules().setComplete());
    }

    // ---- ProgressionRules ----

    @Override
    public int levelDivisor() {
        return catalog.progression().levels().divisor();
    }

    @Override
    public List<LevelTitleView> levelTitles() {
        return levelTitles;
    }

    @Override
    public List<SetView> sets() {
        return sets;
    }

    @Override
    public List<BadgeView> badges() {
        return badges;
    }

    @Override
    public List<QuestView> quests() {
        return quests;
    }

    @Override
    public List<TitleView> titles() {
        return titles;
    }

    private static RewardLineView toView(RewardLine line) {
        return new RewardLineView(line.source().name(), line.amount());
    }

    @Override
    public Optional<RegionView> findRegion(RegionCode code) {
        return catalog.regions().find(code).map(this::toView);
    }

    @Override
    public List<RegionView> activeRegions() {
        return activeRegions;
    }

    @Override
    public List<ProvinceView> provinces() {
        return provinces;
    }

    /** 아이템 정의는 3단계부터 DB(item_definition) — 운영 추가가 바로 보이도록 짧은 캐시(ItemDefinitionCache, 운영 추가 시 비움)로 읽는다. */
    @Override
    public Optional<ItemView> regionItem(RegionCode code) {
        return itemDefinitions.current().find(ItemDefinition.regionItemId(code)).map(ItemViews::of);
    }

    @Override
    public Optional<ItemView> item(String itemId) {
        return itemDefinitions.current().find(itemId).map(ItemViews::of);
    }

    @Override
    public List<ItemView> items() {
        return itemDefinitions.current().stream().map(ItemViews::of).toList();
    }

    @Override
    public RewardRulesView rewardRules() {
        return rewardRules;
    }

    @Override
    public String regionsGeoJson() {
        return catalog.regionsGeoJson();
    }

    private RegionView toView(Region region) {
        Province province = catalog.provinces().require(region.provinceCode());
        return new RegionView(region.code().value(), region.name(), region.provinceCode(), province.name(), region.rarity(), region.countryCode(),
            region.version(), region.replacedBy() == null ? null : region.replacedBy().value(), region.retiredAt());
    }

    private static ProvinceView toView(Province province) {
        return new ProvinceView(province.code(), province.name(), province.fullName(), province.displayOrder(), province.regionCount());
    }
}
