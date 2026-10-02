package com.kobi.territory.exploration.application;

import com.kobi.territory.catalog.api.ProvinceView;
import com.kobi.territory.catalog.api.RegionCatalog;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.RegionDirectory;
import com.kobi.territory.exploration.domain.RegionSnapshot;
import com.kobi.territory.exploration.domain.RewardTable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 카탈로그(상류) Query → 탐험 도메인 VO 변환 어댑터(Anti-Corruption Layer). */
@Component
public class CatalogRegionDirectory implements RegionDirectory {

    private final RegionCatalog catalog;

    public CatalogRegionDirectory(RegionCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Optional<RegionSnapshot> find(RegionCode code) {
        return catalog.findRegion(code).map(r -> new RegionSnapshot(code, r.rarity(), r.provinceCode(), r.retiredAt() != null));
    }

    public RewardTable rewardTable() {
        var rules = catalog.rewardRules();
        return new RewardTable(rules.xpByRarity(), rules.provinceFirstBonus(), rules.claimBonus());
    }

    /** 시·도 코드 → 전체(현행) 지역 수, 표시 순서. */
    public Map<String, Integer> provinceTotals() {
        return catalog.provinces().stream()
            .collect(Collectors.toMap(ProvinceView::code, ProvinceView::regionCount, (a, b) -> a, LinkedHashMap::new));
    }
}
