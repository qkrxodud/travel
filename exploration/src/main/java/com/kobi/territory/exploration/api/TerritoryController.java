package com.kobi.territory.exploration.api;

import com.kobi.territory.catalog.api.ProvinceView;
import com.kobi.territory.catalog.api.RegionCatalog;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.ExplorationDtos.ConquestResponse;
import com.kobi.territory.exploration.api.ExplorationDtos.ProvinceConquestResponse;
import com.kobi.territory.exploration.api.ExplorationDtos.TerritoryResponse;
import com.kobi.territory.exploration.api.ExplorationDtos.VisitResponse;
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
        var o = territories.overview(explorerId, mapId);
        Map<String, ProvinceView> provinces = catalog.provinces().stream()
            .collect(Collectors.toMap(ProvinceView::code, Function.identity()));
        var c = o.conquest();
        return new TerritoryResponse(o.map().id().value(), o.map().name(), o.map().kind().name(),
            new ConquestResponse(c.visited(), c.total(), c.percent()),
            c.provinces().stream().map(p -> new ProvinceConquestResponse(p.provinceCode(),
                provinces.get(p.provinceCode()).name(), p.visited(), p.total(), p.percent(), p.conquered())).toList(),
            o.visits().stream().map(v -> VisitResponse.of(v, catalog)).toList());
    }
}
