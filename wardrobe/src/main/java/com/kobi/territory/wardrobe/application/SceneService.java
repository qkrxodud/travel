package com.kobi.territory.wardrobe.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.wardrobe.api.event.ItemGranted;
import com.kobi.territory.wardrobe.api.event.ItemRevoked;
import com.kobi.territory.wardrobe.api.event.SceneChanged;
import com.kobi.territory.wardrobe.domain.inventory.Inventory;
import com.kobi.territory.wardrobe.domain.inventory.InventoryRepository;
import com.kobi.territory.wardrobe.domain.item.ItemSpecs;
import com.kobi.territory.wardrobe.domain.scene.Holdings;
import com.kobi.territory.wardrobe.domain.scene.Scene;
import com.kobi.territory.wardrobe.domain.scene.SceneEdit;
import com.kobi.territory.wardrobe.domain.scene.SceneRepository;
import com.kobi.territory.wardrobe.domain.scene.SceneUpdate;
import com.kobi.territory.wardrobe.domain.scene.StylePolicy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 장면(Scene) 유스케이스 — 사용자 편집(PUT /scene)과 이벤트(ItemGranted → 자동 착용, ItemRevoked → 벗김).
 * 착용 검증·자동 착용 판단·꾸미기 점수는 Scene 이 한다. 바뀌었을 때만 저장하고 SceneChanged 를 outbox 에 적재한다.
 */
@Service
public class SceneService {

    static final String AGGREGATE = "Scene";

    private final SceneRepository scenes;
    private final InventoryRepository inventories;
    private final WardrobeCatalog catalog;
    private final TerritoryQuery territories;
    private final EventOutbox outbox;
    private final StylePolicy stylePolicy;
    private final Clock clock;
    private final TransactionTemplate writeTx;

    public SceneService(SceneRepository scenes, InventoryRepository inventories, WardrobeCatalog catalog,
                        TerritoryQuery territories, EventOutbox outbox, WardrobeSettings settings, Clock clock,
                        PlatformTransactionManager transactionManager) {
        this.scenes = scenes;
        this.inventories = inventories;
        this.catalog = catalog;
        this.territories = territories;
        this.outbox = outbox;
        this.stylePolicy = settings.stylePolicy();
        this.clock = clock;
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    /** 새로 얻은 아이템 자동 착용(빈 슬롯이거나 더 희귀하면). 정의가 사라진 아이템이면 no-op. */
    @Transactional
    public void onItemGranted(ItemGranted event) {
        Scene scene = load(ExplorerId.of(event.explorerId()));
        ItemSpecs specs = catalog.specsOf(with(scene.wornItemIds(), event.itemId()));
        specs.find(event.itemId())
            .flatMap(item -> scene.autoEquip(item, specs, event.acquiredAt()))
            .ifPresent(update -> saveAndPublish(scene, update));
    }

    /** 회수된 아이템을 입고 있으면 벗긴다. */
    @Transactional
    public void onItemRevoked(ItemRevoked event) {
        Scene scene = load(ExplorerId.of(event.explorerId()));
        scene.takeOff(event.itemId(), event.revokedAt()).ifPresent(update -> saveAndPublish(scene, update));
    }

    /** GET /scene — 탐험가가 없으면 404. */
    @Transactional(readOnly = true)
    public SceneView view(ExplorerId explorerId) {
        territories.personalMapId(explorerId.value());
        return viewOf(load(explorerId));
    }

    /** PUT /scene — 성별·벗기·입기·장식. 입히는 아이템은 가방에 있고 슬롯이 맞아야 한다. */
    public SceneView edit(ExplorerId explorerId, SceneEdit sceneEdit) {
        territories.personalMapId(explorerId.value());
        return writeTx.execute(status -> {
            Scene scene = load(explorerId);
            Inventory inventory = inventories.find(explorerId).orElseGet(() -> Inventory.empty(explorerId, clock.instant()));
            ItemSpecs specs = catalog.specsOf(referencedBy(sceneEdit));
            scene.edit(sceneEdit, specs, Holdings.of(inventory.ownedItems().itemIds()), clock.instant())
                .ifPresent(update -> saveAndPublish(scene, update));
            return viewOf(scene);
        });
    }

    private SceneView viewOf(Scene scene) {
        ItemSpecs wornSpecs = catalog.specsOf(scene.wornItemIds());
        return new SceneView(scene, scene.stylePoints(wornSpecs, stylePolicy), catalog.views(scene.wornItemIds()));
    }

    private void saveAndPublish(Scene scene, SceneUpdate update) {
        scenes.save(scene);
        String explorerId = scene.explorerId().value();
        outbox.append(AGGREGATE, explorerId, new SceneChanged(explorerId, update.at()));
    }

    private Scene load(ExplorerId explorerId) {
        return scenes.find(explorerId).orElseGet(() -> Scene.blank(explorerId, clock.instant()));
    }

    private static List<String> referencedBy(SceneEdit sceneEdit) {
        List<String> itemIds = new ArrayList<>(sceneEdit.equip().values());
        Optional.ofNullable(sceneEdit.props()).ifPresent(itemIds::addAll);
        return itemIds;
    }

    private static List<String> with(List<String> itemIds, String itemId) {
        List<String> all = new ArrayList<>(itemIds);
        all.add(itemId);
        return all;
    }
}
