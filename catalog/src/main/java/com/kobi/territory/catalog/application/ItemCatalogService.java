package com.kobi.territory.catalog.application;

import com.kobi.territory.catalog.api.query.ItemCatalog;
import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.catalog.domain.catalog.Catalog;
import com.kobi.territory.catalog.domain.catalog.CatalogRepository;
import com.kobi.territory.catalog.domain.item.ItemDefinition;
import com.kobi.territory.catalog.domain.item.ItemDefinitionRepository;
import com.kobi.territory.common.model.RegionCode;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이템 정의 유스케이스 — {@link ItemCatalog} 공개 Query 구현 + 운영 추가(POST /admin/items).
 * 지급 규칙 판정·검증은 ItemDefinitions·ItemDefinition·Catalog(도메인)가 한다. 조회는 짧은 캐시(ItemDefinitionCache)를 쓴다.
 */
@Service
public class ItemCatalogService implements ItemCatalog {

    private final ItemDefinitionRepository items;
    private final ItemDefinitionCache cache;
    private final Catalog catalog;
    private final Clock clock;

    public ItemCatalogService(ItemDefinitionRepository items, ItemDefinitionCache cache, CatalogRepository catalogs,
                              Clock clock) {
        this.items = items;
        this.cache = cache;
        this.clock = clock;
        this.catalog = catalogs.load();
    }

    /** 기동 시 정합성: 지역마다 특산물 아이템이 있어야 한다(V3_1 이관 데이터) — 어긋나면 기동 실패. */
    @PostConstruct
    public void verifyCoverage() {
        catalog.requireItemCoverage(cache.current());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ItemView> item(String itemId) {
        return cache.current().find(itemId).map(ItemViews::of);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemView> items(Collection<String> itemIds) {
        return cache.current().findAll(itemIds).stream().map(ItemViews::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemView> grantedByCheckIn(String regionCode, String provinceCode, Instant processedAt) {
        return cache.current().grantedByCheckIn(RegionCode.of(regionCode), provinceCode, dayOf(processedAt), processedAt).stream()
            .map(ItemViews::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemView> grantedByThemeCompletion(String setId, Instant completedAt) {
        return cache.current().grantedByThemeCompletion(setId, dayOf(completedAt)).stream().map(ItemViews::of).toList();
    }

    private LocalDate dayOf(Instant at) {
        return LocalDate.ofInstant(at, clock.getZone());
    }

    /** 운영 추가: 형식 검증(도메인 생성자) → 참조 검증(Catalog) → 예약 id·중복 검증(ItemDefinitions, 최신 DB 기준) → 저장 → 캐시 비움. */
    @Transactional
    public ItemView register(RegisterItemCommand command) {
        ItemDefinition item = command.toDefinition(clock.instant());
        catalog.requireReferences(item);
        items.loadAll().requireRegistrable(item);
        items.add(item);
        cache.invalidate();
        return ItemViews.of(item);
    }
}
