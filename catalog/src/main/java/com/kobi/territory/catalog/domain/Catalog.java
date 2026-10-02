package com.kobi.territory.catalog.domain;

import java.util.Objects;

/**
 * 카탈로그 참조 데이터 묶음(애그리거트 아님, 시작 시 메모리에 올린다). 생성 시 정합성을 검증한다:
 * 지역 코드 유일, 시·도 소속·지역 수 일치, 지역마다 특산물 아이템 1개. 어긋나면 기동을 실패시킨다.
 */
public record Catalog(Regions regions, Provinces provinces, ItemDefinitions items, RewardRules rewardRules,
                      String regionsGeoJson) {
    public Catalog {
        Objects.requireNonNull(regions, "regions");
        Objects.requireNonNull(provinces, "provinces");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(rewardRules, "rewardRules");
        Objects.requireNonNull(regionsGeoJson, "regionsGeoJson");
        provinces.requireConsistentWith(regions);
        items.requireCoverage(regions);
    }
}
