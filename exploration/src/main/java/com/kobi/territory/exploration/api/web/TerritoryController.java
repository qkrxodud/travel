package com.kobi.territory.exploration.api.web;

import com.kobi.territory.catalog.api.query.ProvinceView;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.web.ExplorationDtos.ClaimResponse;
import com.kobi.territory.exploration.api.web.ExplorationDtos.ConquestResponse;
import com.kobi.territory.exploration.api.web.ExplorationDtos.ProvinceConquestResponse;
import com.kobi.territory.exploration.api.web.ExplorationDtos.TerritoryResponse;
import com.kobi.territory.exploration.api.web.ExplorationDtos.VisitResponse;
import com.kobi.territory.exploration.application.TerritoryQueryService;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 영토 조회: 방문 목록(방문일 최근 순) + 전국·시도별 정복률(시·도는 카탈로그 표시 순서). */
@RestController
public class TerritoryController {

    private final TerritoryQueryService territories;
    private final RegionCatalog catalog;

    public TerritoryController(TerritoryQueryService territories, RegionCatalog catalog) {
        this.territories = territories;
        this.catalog = catalog;
    }

    @GetMapping("/territory")
    public TerritoryResponse territory(@CurrentExplorer ExplorerId explorerId,
                                       @RequestParam(value = "mapId", required = false) String mapId) {
        var overview = territories.overview(explorerId, mapId);
        Map<String, ProvinceView> provinces = catalog.provinces().stream()
            .collect(Collectors.toMap(ProvinceView::code, Function.identity()));
        var conquest = overview.conquest();
        return new TerritoryResponse(overview.map().id().value(), overview.map().name(), overview.map().kind().name(),
            new ConquestResponse(conquest.visited(), conquest.total(), conquest.percent()),
            conquest.provinces().stream().map(provinceRate -> new ProvinceConquestResponse(provinceRate.provinceCode(),
                provinces.get(provinceRate.provinceCode()).name(), provinceRate.visited(), provinceRate.total(), provinceRate.percent(), provinceRate.conquered())).toList(),
            overview.visits().stream().map(view -> VisitResponse.of(view, catalog)).toList(),
            overview.claims().stream().map(claim -> new ClaimResponse(claim.regionCode().value(), claim.checkedInBy().value())).toList());
    }
}
