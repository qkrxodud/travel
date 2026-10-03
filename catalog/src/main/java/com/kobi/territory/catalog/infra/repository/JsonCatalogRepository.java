package com.kobi.territory.catalog.infra.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.catalog.domain.definition.BadgeCondition;
import com.kobi.territory.catalog.domain.definition.BadgeDefinition;
import com.kobi.territory.catalog.domain.catalog.Catalog;
import com.kobi.territory.catalog.domain.definition.ThemeDefinition;
import com.kobi.territory.catalog.domain.definition.LevelRules;
import com.kobi.territory.catalog.domain.definition.LevelTitle;
import com.kobi.territory.catalog.domain.definition.ProgressionDefinitions;
import com.kobi.territory.catalog.domain.definition.QuestDefinition;
import com.kobi.territory.catalog.domain.definition.StreakMilestone;
import com.kobi.territory.catalog.domain.definition.StreakRules;
import com.kobi.territory.catalog.domain.mystery.MysteryRules;
import com.kobi.territory.catalog.domain.catalog.CatalogRepository;
import com.kobi.territory.catalog.domain.region.Province;
import com.kobi.territory.catalog.domain.region.Provinces;
import com.kobi.territory.catalog.domain.region.Region;
import com.kobi.territory.catalog.domain.region.Regions;
import com.kobi.territory.catalog.domain.reward.RewardRules;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * classpath:catalog/*.json 을 시작 시 한 번 읽어 메모리에 올린다(지역·시·도·보상·진행 정의 — 아이템 정의는 3단계부터
 * DB item_definition, {@link JpaItemDefinitionRepository}).
 * 데이터 원본: 프로토타입 doc/나의 영토.html → tools/catalog/gen-catalog.js 로 생성.
 * 파싱만 하고, 정합성 검증은 도메인 Catalog 생성자가 한다(어긋나면 기동 실패).
 */
@Repository
public class JsonCatalogRepository implements CatalogRepository {

    private static final String BASE = "catalog/";

    private final Catalog catalog;

    public JsonCatalogRepository() {
        ObjectMapper om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<Province> provinces = read(om, "provinces.json", new TypeReference<List<ProvinceJson>>() {}).stream()
            .map(provinceJson -> new Province(provinceJson.code, provinceJson.name, provinceJson.fullName, provinceJson.displayOrder, provinceJson.regionCount)).toList();
        List<Region> regions = read(om, "regions.json", new TypeReference<List<RegionJson>>() {}).stream()
            .map(regionJson -> new Region(RegionCode.of(regionJson.code), regionJson.name, regionJson.provinceCode, Rarity.valueOf(regionJson.rarity), regionJson.countryCode,
                regionJson.version, regionJson.replacedBy == null ? null : RegionCode.of(regionJson.replacedBy),
                regionJson.retiredAt == null ? null : LocalDate.parse(regionJson.retiredAt)))
            .toList();
        RewardJson rj = read(om, "reward-rules.json", new TypeReference<RewardJson>() {});
        Map<Rarity, Integer> xp = new HashMap<>();
        rj.xpByRarity.forEach((rarityName, amount) -> xp.put(Rarity.valueOf(rarityName), amount));
        // 정합성 검증은 도메인(Catalog·일급 컬렉션)이 생성 시 한다
        this.catalog = new Catalog(Regions.of(regions), Provinces.of(provinces),
            new RewardRules(xp, rj.provinceFirstBonus, rj.setCompleteBonus, rj.claimBonus, rj.mysteryBonus,
                rj.provinceConquestBonus), readString("regions.geojson"),
            progression(om), mystery(om));
    }

    /** 8단계 이번 주 미스터리 지역 규칙: mystery.json */
    private static MysteryRules mystery(ObjectMapper om) {
        MysteryJson mj = read(om, "mystery.json", new TypeReference<MysteryJson>() {});
        return new MysteryRules(mj.rarities.stream().map(Rarity::valueOf).collect(Collectors.toSet()), mj.bottomFraction);
    }

    /** 2단계 진행 정의: levels.json, sets.json, badges.json, quests.json (+ 8단계 streak-rules.json) */
    private static ProgressionDefinitions progression(ObjectMapper om) {
        LevelsJson lj = read(om, "levels.json", new TypeReference<LevelsJson>() {});
        List<ThemeDefinition> themes = read(om, "sets.json", new TypeReference<List<ThemeJson>>() {}).stream()
            .map(themeJson -> new ThemeDefinition(themeJson.id, themeJson.name, themeJson.desc, themeJson.title,
                themeJson.regionCodes.stream().map(RegionCode::of).toList(),
                themeJson.background == null ? null
                    : new ThemeDefinition.Background(themeJson.background.emoji, themeJson.background.name, themeJson.background.theme)))
            .toList();
        List<BadgeDefinition> badges = read(om, "badges.json", new TypeReference<List<BadgeJson>>() {}).stream()
            .map(badgeJson -> new BadgeDefinition(badgeJson.id, badgeJson.ico, badgeJson.name, badgeJson.desc, new BadgeCondition(
                BadgeCondition.Type.valueOf(badgeJson.condition.type), badgeJson.condition.min, badgeJson.condition.provinces, badgeJson.condition.groups,
                badgeJson.condition.ratio)))
            .toList();
        List<QuestDefinition> quests = read(om, "quests.json", new TypeReference<List<QuestJson>>() {}).stream()
            .map(questJson -> new   QuestDefinition(questJson.id, QuestDefinition.Scope.valueOf(questJson.scope), questJson.ico, questJson.name, questJson.desc,
                QuestDefinition.Metric.valueOf(questJson.metric), questJson.param, questJson.target, questJson.xp, questJson.title))
            .toList();
        return new ProgressionDefinitions(
            new LevelRules(lj.divisor, lj.titles.stream().map(titleJson -> new LevelTitle(titleJson.level, titleJson.name)).toList()),
            themes, badges, quests, streak(om));
    }

    private static StreakRules streak(ObjectMapper om) {
        StreakRulesJson sj = read(om, "streak-rules.json", new TypeReference<StreakRulesJson>() {});
        return new StreakRules(sj.freeze.maxHeld, sj.freeze.monthlyQuestsReward, sj.milestones.stream()
            .map(milestoneJson -> new StreakMilestone(milestoneJson.months, milestoneJson.xp, milestoneJson.freezes, milestoneJson.title))
            .toList());
    }

    @Override
    public Catalog load() {
        return catalog;
    }

    private static <T> T read(ObjectMapper om, String name, TypeReference<T> type) {
        try (InputStream in = open(name)) {
            return om.readValue(in, type);
        } catch (IOException exception) {
            throw new UncheckedIOException("카탈로그 로딩 실패: " + name, exception);
        }
    }

    private static String readString(String name) {
        try (InputStream in = open(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("카탈로그 로딩 실패: " + name, exception);
        }
    }

    private static InputStream open(String name) throws IOException {
        InputStream in = JsonCatalogRepository.class.getClassLoader().getResourceAsStream(BASE + name);
        if (in == null) throw new IOException("classpath:" + BASE + name + " 없음");
        return in;
    }

    record ProvinceJson(String code, String name, String fullName, int displayOrder, int regionCount) {}

    record RegionJson(String code, String name, String provinceCode, String rarity, String countryCode, int version,
                      String replacedBy, String retiredAt) {}

    record LevelTitleJson(int level, String name) {}

    record LevelsJson(int divisor, List<LevelTitleJson> titles) {}

    record BackgroundJson(String emoji, String name, String theme) {}

    record ThemeJson(String id, String name, String desc, String title, List<String> regionCodes, BackgroundJson background) {}

    record ConditionJson(String type, int min, List<String> provinces, List<List<String>> groups, double ratio) {}

    record BadgeJson(String id, String ico, String name, String desc, ConditionJson condition) {}

    record QuestJson(String id, String scope, String ico, String name, String desc, String metric, int param, int target,
                     int xp, String title) {}

    record RewardJson(Map<String, Integer> xpByRarity, int provinceFirstBonus, int setCompleteBonus, int claimBonus,
                      int mysteryBonus, int provinceConquestBonus) {}

    record FreezeJson(int maxHeld, int monthlyQuestsReward) {}

    record MilestoneJson(int months, int xp, int freezes, String title) {}

    record StreakRulesJson(FreezeJson freeze, List<MilestoneJson> milestones) {}

    record MysteryJson(List<String> rarities, double bottomFraction) {}
}
