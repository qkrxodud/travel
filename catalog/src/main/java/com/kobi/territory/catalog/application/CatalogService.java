package com.kobi.territory.catalog.application;

import com.kobi.territory.catalog.api.ItemView;
import com.kobi.territory.catalog.api.ProvinceView;
import com.kobi.territory.catalog.api.RegionCatalog;
import com.kobi.territory.catalog.api.RegionView;
import com.kobi.territory.catalog.api.RewardRulesView;
import com.kobi.territory.catalog.domain.Catalog;
import com.kobi.territory.catalog.domain.CatalogRepository;
import com.kobi.territory.catalog.domain.ItemDefinition;
import com.kobi.territory.catalog.domain.Province;
import com.kobi.territory.catalog.domain.Region;
import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * {@link RegionCatalog} 구현 — 저장소에서 Catalog를 불러와 도메인 컬렉션에 묻고 view로 매핑만 한다.
 * 필터·정렬·검증은 Regions·Provinces·ItemDefinitions·Catalog(도메인)가 한다.
 */
@Service
public class CatalogService implements RegionCatalog {

    private final Catalog catalog;
    private final List<RegionView> activeRegions;
    private final List<ProvinceView> provinces;
    private final List<ItemView> items;
    private final RewardRulesView rewardRules;

    public CatalogService(CatalogRepository repository) {
        this.catalog = repository.load();
        this.activeRegions = catalog.regions().active().stream().map(this::toView).toList();
        this.provinces = catalog.provinces().inDisplayOrder().stream().map(CatalogService::toView).toList();
        this.items = catalog.items().all().stream().map(CatalogService::toView).toList();
        var rr = catalog.rewardRules();
        this.rewardRules = new RewardRulesView(rr.xpByRarity(), rr.provinceFirstBonus(), rr.setCompleteBonus(), rr.claimBonus());
    }

    @Override
    public Optional<RegionView> findRegion(RegionCode code) {
        return catalog.regions().find(code).map(this::toView);
    }

    @Override
    public List<RegionView> activeRegions() {
        return activeRegions;
    }

    @Override
    public List<ProvinceView> provinces() {
        return provinces;
    }

    @Override
    public Optional<ItemView> regionItem(RegionCode code) {
        return catalog.items().regionItem(code).map(CatalogService::toView);
    }

    @Override
    public Optional<ItemView> item(String itemId) {
        return catalog.items().find(itemId).map(CatalogService::toView);
    }

    @Override
    public List<ItemView> items() {
        return items;
    }

    @Override
    public RewardRulesView rewardRules() {
        return rewardRules;
    }

    @Override
    public String regionsGeoJson() {
        return catalog.regionsGeoJson();
    }

    private RegionView toView(Region r) {
        Province p = catalog.provinces().require(r.provinceCode());
        return new RegionView(r.code().value(), r.name(), r.provinceCode(), p.name(), r.rarity(), r.countryCode(),
            r.version(), r.replacedBy() == null ? null : r.replacedBy().value(), r.retiredAt());
    }

    private static ProvinceView toView(Province p) {
        return new ProvinceView(p.code(), p.name(), p.fullName(), p.displayOrder(), p.regionCount());
    }

    private static ItemView toView(ItemDefinition i) {
        return new ItemView(i.itemId(), i.regionCode() == null ? null : i.regionCode().value(), i.name(), i.emoji(),
            i.slot().name(), i.tier(), i.theme(),
            i.look() == null ? null : new ItemView.Look(i.look().type(), i.look().primary(), i.look().secondary()));
    }
}
