package com.kobi.territory.catalog.domain.catalog;

import com.kobi.territory.catalog.domain.definition.ProgressionDefinitions;
import com.kobi.territory.catalog.domain.definition.TitleDefinition;
import com.kobi.territory.catalog.domain.item.ItemDefinitions;
import com.kobi.territory.catalog.domain.region.Provinces;
import com.kobi.territory.catalog.domain.region.Regions;
import com.kobi.territory.catalog.domain.reward.RewardRules;
import java.util.List;
import java.util.Objects;

/**
 * 카탈로그 참조 데이터 묶음(애그리거트 아님, 시작 시 메모리에 올린다). 생성 시 정합성을 검증한다:
 * 지역 코드 유일, 시·도 소속·지역 수 일치, 지역마다 특산물 아이템 1개, 진행 정의(세트·뱃지)의 지역·시·도 참조.
 * 어긋나면 기동을 실패시킨다.
 */
public record Catalog(Regions regions, Provinces provinces, ItemDefinitions items, RewardRules rewardRules,
                      String regionsGeoJson, ProgressionDefinitions progression) {
    public Catalog {
        Objects.requireNonNull(regions, "regions");
        Objects.requireNonNull(provinces, "provinces");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(rewardRules, "rewardRules");
        Objects.requireNonNull(regionsGeoJson, "regionsGeoJson");
        Objects.requireNonNull(progression, "progression");
        provinces.requireConsistentWith(regions);
        items.requireCoverage(regions);
        progression.requireConsistentWith(regions, provinces);
    }

    /** 진행 정의 없이(1단계 범위) 만든다. */
    public Catalog(Regions regions, Provinces provinces, ItemDefinitions items, RewardRules rewardRules, String regionsGeoJson) {
        this(regions, provinces, items, rewardRules, regionsGeoJson, ProgressionDefinitions.empty());
    }

    /** 칭호 전체(진행 정의 + 시·도). */
    public List<TitleDefinition> titles() {
        return progression.titles(provinces);
    }
}
