package com.kobi.territory.catalog.domain.catalog;

import com.kobi.territory.catalog.domain.CatalogError;
import com.kobi.territory.catalog.domain.definition.ProgressionDefinitions;
import com.kobi.territory.catalog.domain.definition.TitleDefinition;
import com.kobi.territory.catalog.domain.item.GrantRule;
import com.kobi.territory.catalog.domain.item.ItemDefinition;
import com.kobi.territory.catalog.domain.item.ItemDefinitions;
import com.kobi.territory.catalog.domain.mystery.MysteryRules;
import com.kobi.territory.catalog.domain.region.Provinces;
import com.kobi.territory.catalog.domain.region.RegionBoundaries;
import com.kobi.territory.catalog.domain.region.RegionLocator;
import com.kobi.territory.catalog.domain.region.Regions;
import com.kobi.territory.catalog.domain.reward.RewardRules;
import java.util.List;
import java.util.Objects;

/**
 * 카탈로그 정적 참조 데이터 묶음(애그리거트 아님, 시작 시 메모리에 올린다). 생성 시 정합성을 검증한다:
 * 지역 코드 유일, 시·도 소속·지역 수 일치, 진행 정의(세트·뱃지)의 지역·시·도 참조. 어긋나면 기동을 실패시킨다.
 * 아이템 정의는 3단계부터 운영이 추가하는 DB 데이터라 여기 없다({@link ItemDefinitions} — 지역마다 아이템이 있는지는
 * {@link #requireItemCoverage}로, 새 아이템의 참조는 {@link #requireReferences}로 검증한다).
 */
public record Catalog(Regions regions, Provinces provinces, RewardRules rewardRules, String regionsGeoJson,
                      ProgressionDefinitions progression, MysteryRules mystery, RegionBoundaries boundaries) {
    public Catalog {
        Objects.requireNonNull(regions, "regions");
        Objects.requireNonNull(provinces, "provinces");
        Objects.requireNonNull(rewardRules, "rewardRules");
        Objects.requireNonNull(regionsGeoJson, "regionsGeoJson");
        Objects.requireNonNull(progression, "progression");
        Objects.requireNonNull(mystery, "mystery");
        Objects.requireNonNull(boundaries, "boundaries");
        provinces.requireConsistentWith(regions);
        progression.requireConsistentWith(regions, provinces);
        boundaries.requireKnownIn(regions);
    }

    /** 경계 자료(점-다각형 판정) 없이 — 13s단계 이전 범위. */
    public Catalog(Regions regions, Provinces provinces, RewardRules rewardRules, String regionsGeoJson,
                   ProgressionDefinitions progression, MysteryRules mystery) {
        this(regions, provinces, rewardRules, regionsGeoJson, progression, mystery, RegionBoundaries.none());
    }

    /** 미스터리 규칙 없이(8단계 이전 범위) 만든다. */
    public Catalog(Regions regions, Provinces provinces, RewardRules rewardRules, String regionsGeoJson,
                   ProgressionDefinitions progression) {
        this(regions, provinces, rewardRules, regionsGeoJson, progression, MysteryRules.none());
    }

    /** 진행 정의 없이(1단계 범위) 만든다. */
    public Catalog(Regions regions, Provinces provinces, RewardRules rewardRules, String regionsGeoJson) {
        this(regions, provinces, rewardRules, regionsGeoJson, ProgressionDefinitions.empty());
    }

    /** 바깥 위치(좌표·주소) → 우리 지역(13s단계). @param toleranceKilometers 경계 밖 점을 가장 가까운 지역으로 볼 거리 */
    public RegionLocator regionLocator(double toleranceKilometers) {
        return new RegionLocator(regions, provinces, boundaries, toleranceKilometers);
    }

    /** 칭호 전체(진행 정의 + 시·도). */
    public List<TitleDefinition> titles() {
        return progression.titles(provinces);
    }

    /** 지역마다 특산물 아이템이 있어야 한다(기동 시 검증 — 어긋나면 기동 실패). */
    public void requireItemCoverage(ItemDefinitions items) {
        items.requireCoverage(regions);
    }

    /** 운영이 추가하는 아이템의 지급 규칙이 아는 지역·시·도·테마를 가리키는지. 모르면 UNKNOWN_ITEM_REFERENCE. */
    public void requireReferences(ItemDefinition item) {
        GrantRule rule = item.grantRule();
        boolean known = switch (rule) {
            case GrantRule.RegionVisit regionVisit -> regions.find(regionVisit.region()).isPresent();
            case GrantRule.ProvinceCheckIn provinceCheckIn -> provinces.find(provinceCheckIn.provinceCode()).isPresent();
            case GrantRule.ThemeComplete themeComplete -> progression.themes().stream()
                .anyMatch(theme -> theme.id().equals(themeComplete.themeId()));
            case GrantRule.PeriodCheckIn periodCheckIn -> true;
            case GrantRule.Manual manual -> true;
            case GrantRule.Invitation invitation -> true;
            case GrantRule.ProvinceComplete provinceComplete -> provinces.find(provinceComplete.provinceCode()).isPresent();
            case GrantRule.StreakMilestone streakMilestone -> progression.streak().defines(streakMilestone.months());
            case GrantRule.SeasonComplete seasonComplete -> progression.definesSeasonRound(seasonComplete.roundId());
        };
        if (!known) throw CatalogError.UNKNOWN_ITEM_REFERENCE.exception("지급 규칙이 모르는 대상을 가리킵니다: " + rule.type() + " " + rule.ref());
    }
}
