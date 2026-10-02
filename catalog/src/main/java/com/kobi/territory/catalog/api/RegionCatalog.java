package com.kobi.territory.catalog.api;

import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import java.util.Optional;

/**
 * 카탈로그 공개 Query(Published Language). 다른 컨텍스트는 이 인터페이스와 view record만 참조한다.
 */
public interface RegionCatalog {

    Optional<RegionView> findRegion(RegionCode code);

    /** 폐지되지 않은 지역 전체(현재 250개). */
    List<RegionView> activeRegions();

    /** 시·도 17개, 표시 순서대로. */
    List<ProvinceView> provinces();

    /** 지역 체크인 시 지급되는 특산물 아이템. */
    Optional<ItemView> regionItem(RegionCode code);

    /** itemId로 아이템 정의 조회(예: region:KR-11010). */
    Optional<ItemView> item(String itemId);

    List<ItemView> items();

    RewardRulesView rewardRules();

    /** 지역 경계 GeoJSON FeatureCollection 원문. */
    String regionsGeoJson();
}
