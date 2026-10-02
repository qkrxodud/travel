package com.kobi.territory.catalog.api;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 카탈로그 조회 API. 정적 참조 데이터라 탐험가 식별 없이 열려 있다. */
@RestController
@RequestMapping("/catalog")
public class CatalogController {

    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

    private final RegionCatalog catalog;

    public CatalogController(RegionCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/regions")
    public List<RegionView> regions() {
        return catalog.activeRegions();
    }

    @GetMapping(value = "/regions.geojson")
    public ResponseEntity<String> regionsGeoJson() {
        return ResponseEntity.ok()
            .contentType(GEO_JSON)
            .cacheControl(CacheControl.noCache())
            .body(catalog.regionsGeoJson());
    }

    @GetMapping("/provinces")
    public List<ProvinceView> provinces() {
        return catalog.provinces();
    }

    @GetMapping("/items")
    public List<ItemView> items() {
        return catalog.items();
    }

    @GetMapping("/reward-rules")
    public RewardRulesView rewardRules() {
        return catalog.rewardRules();
    }
}
