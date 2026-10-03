package com.kobi.territory.exploration.application;

import com.kobi.territory.catalog.api.query.MysteryRegionQuery;
import com.kobi.territory.catalog.api.query.ProvinceView;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RewardCalculator;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.territory.CheckInPreview;
import com.kobi.territory.exploration.domain.territory.CheckInRewards;
import com.kobi.territory.exploration.domain.territory.RegionDirectory;
import com.kobi.territory.exploration.domain.territory.RegionSnapshot;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 카탈로그(상류) Query → 탐험 도메인 VO·포트 변환 어댑터(Anti-Corruption Layer).
 * 보상 계산은 카탈로그의 공개 함수(RewardCalculator)에 위임한다 — 진행 컨텍스트와 같은 함수(D1).
 */
@Component
public class CatalogRegionDirectory implements RegionDirectory, CheckInRewards {

    private final RegionCatalog catalog;
    private final RewardCalculator rewards;
    private final MysteryRegionQuery mysteries;

    public CatalogRegionDirectory(RegionCatalog catalog, RewardCalculator rewards, MysteryRegionQuery mysteries) {
        this.catalog = catalog;
        this.rewards = rewards;
        this.mysteries = mysteries;
    }

    @Override
    public Optional<RegionSnapshot> find(RegionCode code) {
        return catalog.findRegion(code).map(region -> new RegionSnapshot(code, region.rarity(), region.provinceCode(), region.retiredAt() != null));
    }

    @Override
    public List<CheckInPreview.XpLine> award(Rarity rarity, boolean firstInProvince, boolean firstClaim, boolean mysteryOfWeek) {
        return rewards.checkIn(rarity, firstInProvince, firstClaim, mysteryOfWeek).stream()
            .map(line -> new CheckInPreview.XpLine(CheckInPreview.XpSource.valueOf(line.source()), line.amount()))
            .toList();
    }

    /** 처리 시각 at 이 속한 주의 미스터리 지역(8단계 — 지금 주면 카탈로그가 골라 기록, 기록 없는 지난 주는 비어 있음). */
    public Optional<RegionCode> mysteryRegionAt(Instant at) {
        return mysteries.weekOf(at).map(week -> RegionCode.of(week.regionCode()));
    }

    /** 시·도 코드 → 전체(현행) 지역 수, 표시 순서. */
    public Map<String, Integer> provinceTotals() {
        return catalog.provinces().stream()
            .collect(Collectors.toMap(ProvinceView::code, ProvinceView::regionCount, (first, second) -> first, LinkedHashMap::new));
    }
}
